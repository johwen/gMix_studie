/*
 * gMix open source project - https://svs.informatik.uni-hamburg.de/gmix/
 * Copyright (C) 2012  Karl-Peter Fuchs
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package evaluation.loadGenerator.traceBasedTraffic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import evaluation.loadGenerator.LoadGenerator;
import evaluation.loadGenerator.LoadGenerator.InsertLevel;
import framework.core.AnonNode;
import framework.core.launcher.ToolName;
import framework.core.routing.RoutingMode;
import framework.core.socket.socketInterfaces.StreamAnonSocket;
import framework.core.socket.socketInterfaces.AnonSocketOptions.CommunicationMode;
import framework.core.util.Util;


/**
 * MP12-13 - client ratio
 */
public class TBT_RR_LoadGenerator_modified extends TBT_LoadGenerator {
	
	private AnonNode client;

	
	
	protected TBT_RR_LoadGenerator_modified(LoadGenerator owner) {
		super(owner);
		System.out.println("LOAD_GENERATOR (AFAP): start at " +System.currentTimeMillis());
		// create client
		owner.commandLineParameters.gMixTool = ToolName.CLIENT;
		this.client = new AnonNode(owner.commandLineParameters);
		int numberOfConnections;
		int numberOfActiveConnections=0;
		if (owner.INSERT_LEVEL == InsertLevel.APPLICATION_LEVEL){
			numberOfConnections = settings.getPropertyAsInt("AL-AFAP-NUMBER_OF_CLIENTS");
			numberOfActiveConnections = settings.getPropertyAsInt("NUMBER_OF_ACTIVE_CLIENTS");
		}
		else if (owner.INSERT_LEVEL == InsertLevel.MIX_PACKET_LEVEL)
			numberOfConnections = settings.getPropertyAsInt("MPL-AFAP-NUMBER_OF_CLIENTS");
		else
			throw new RuntimeException("unknown mode: " +owner.INSERT_LEVEL); 
		
		int connectionsPerThread = numberOfConnections / client.NUMBER_OF_THREADS;
		for (int i=0; i<client.NUMBER_OF_THREADS; i++)
			new RequestThread(connectionsPerThread, numberOfActiveConnections/client.NUMBER_OF_THREADS).start();
	}

	
	private class RequestThread extends Thread {

		private StreamAnonSocket[] sockets;
		private OutputStream[] outputStreams;
		private InputStream[] inputStreams;
		private Random random = new Random();
		private int activeClients;
		
		
		public RequestThread(int numberOfConnections, int numberOfActiveConnections) {
			this.sockets = new StreamAnonSocket[numberOfConnections];
			this.outputStreams = new OutputStream[numberOfConnections];
			this.activeClients = numberOfActiveConnections;
			if (client.IS_DUPLEX)
				this.inputStreams = new InputStream[numberOfConnections];
		}
		

		@Override
		public void run() {
			try {
				// create and connect sockets:
				System.out.println("LOAD_GENERATOR (TBT): creating " +sockets.length +" connections...");
				CommunicationMode cm = client.IS_DUPLEX ? CommunicationMode.DUPLEX : CommunicationMode.SIMPLEX_SENDER;
				for (int i=0; i<sockets.length; i++) {
					sockets[i] = client.createStreamSocket(cm, client.ROUTING_MODE != RoutingMode.CASCADE);
					sockets[i].connect(settings.getPropertyAsInt("SERVICE_PORT1"));
					outputStreams[i] = sockets[i].getOutputStream();
					if (client.IS_DUPLEX)
						inputStreams[i] = sockets[i].getInputStream();	
				} 
				System.out.println("LOAD_GENERATOR (AFAP): creating " +sockets.length +" connections done: start sending");
				if (client.IS_DUPLEX)
					new ReplyThread(sockets, inputStreams, activeClients).start();
				// start sending
				while (true) {
					for (int i=0; i<activeClients; i++) {
						byte[] data = new byte[sockets[i].getMTU()];
						random.nextBytes(data);
						outputStreams[i].write(data);
						outputStreams[i].flush();
					
					} 
//					try{
//						Thread.sleep(1000);
//					}catch(InterruptedException ie){
//						ie.printStackTrace();
//					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
	
	
	private class ReplyThread extends Thread {
		
		private StreamAnonSocket[] sockets;
		private InputStream[] inputStreams;
		private int activeClients;
		
		
		public ReplyThread(StreamAnonSocket[] sockets, InputStream[] inputStreams, int activeClients) {
			this.sockets = sockets;
			this.inputStreams = inputStreams;
			this.activeClients = activeClients;
		}

		
		@Override
		public void run() {
			while (true) {
				try {
					for (int i=0; i<activeClients; i++) {
						byte[] data = new byte[sockets[i].getMTU()];
						Util.forceRead(inputStreams[i], data);
					} 
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
}
