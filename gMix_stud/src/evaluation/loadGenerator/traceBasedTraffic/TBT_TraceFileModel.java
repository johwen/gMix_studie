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

import java.io.BufferedReader;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.Vector;

import evaluation.loadGenerator.LoadGenerator;
import evaluation.simulator.Simulator;
import evaluation.simulator.core.networkComponent.AbstractClient;
import evaluation.simulator.plugins.trafficSource.TraceReplayClient;
import evaluation.simulator.plugins.trafficSource.TrafficSourceImplementation;
import evaluation.traceParser.engine.dataStructure.Flow;
import evaluation.traceParser.engine.fileReader.FlowReader;
import framework.core.config.Settings;


public class TBT_TraceFileModel {

private Settings settings;
private TBT_TraceReplayClient[] clients;


	//	private TBT_TraceReplayClient[] clients;
	public TBT_TraceFileModel(Settings settings){

		this.settings = settings; 
	}
	

	public TBT_TraceReplayClient[] createClientsArray() {
		//String pathToTrace = LoadGenerator.
		String pathToTrace = settings.getProperty("AL-TBT_TraceFile-NAME");
				
		boolean limitClients = settings.getPropertyAsBoolean("AL-TBT-LIMIT_CLIENTS");
		int limit = limitClients ? settings.getPropertyAsInt("AL-TBT-NUMBER_OF_CLIENTS") : 0;
		Vector<TBT_TraceReplayClient> clients = new Vector<TBT_TraceReplayClient>(1000);
		try {
			FlowReader flowReader = new FlowReader(pathToTrace, null, false);
			int currentClientId = -1;
			while (flowReader.hasNextFlow()) {
				Flow flow = flowReader.readFlow();
				if (currentClientId != flow.senderId) {
					currentClientId ++;
					if (limitClients && currentClientId == limit)
						break;
					if (flow.senderId != currentClientId)
						throw new RuntimeException("the trace file " +pathToTrace +" seems to be not " +
								"ordered correctly.\nmake sure to use an Extractor (package evaluation." +
								"traceParser.scenarioExtractor) to generate the trace file and specify " +
								"the path of the created trace file in the experiment config file correctly.)" );
					long offset = flowReader.getOffsetOfLastFlow();
					FlowReader readerOfClient;
					try {
						RandomAccessFile raf = new RandomAccessFile(pathToTrace, "r");
						raf.seek(offset);
						// use BufferedReader to read line instead of raf.readLine() for performance reasons:
						BufferedReader reader = new BufferedReader(Channels.newReader(raf.getChannel(), "ISO-8859-1"));
						readerOfClient = new FlowReader(reader, null, true);
					} catch (Exception e) {
						System.err.println("could not read " +pathToTrace); 
						e.printStackTrace();
						throw new RuntimeException();
					}
					TBT_TraceReplayClient client = new TBT_TraceReplayClient("Client" +currentClientId, readerOfClient, currentClientId);
					clients.add(client);
				}
			}
		} catch (Exception e) {
			System.err.println("could not read trace file " +pathToTrace); 
			e.printStackTrace();
			throw new RuntimeException(); 
		}
		this.clients = clients.toArray(new TBT_TraceReplayClient[0]); 
		return this.clients;
	}

	
	public void startScheduling() {
		for (TBT_TraceReplayClient client: clients)
			client.startScheduling();
	}

}
