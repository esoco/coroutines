//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'coroutines' project.
// Copyright 2018 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
package de.esoco.coroutine;

import de.esoco.lib.logging.Profiler;

import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import java.util.concurrent.CountDownLatch;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.CodeExecution.run;
import static de.esoco.coroutine.step.nio.FileRead.readFrom;
import static de.esoco.coroutine.step.nio.FileWrite.writeTo;
import static de.esoco.coroutine.step.nio.SocketReceive.contentFullyRead;
import static de.esoco.coroutine.step.nio.SocketReceive.receiveUntil;
import static de.esoco.coroutine.step.nio.SocketSend.sendTo;

import static de.esoco.lib.datatype.Range.from;


/********************************************************************
 * Demo of {@link Coroutine} features.
 *
 * @author eso
 */
public class CoroutineDemo
{
	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * A demo of asynchronous file I/O based on Jav NIO.
	 */
	public static void demoFileIO()
	{
		Coroutine<ByteBuffer, ?> fileWrite =
			first(writeTo("test.file", StandardOpenOption.CREATE));

		Coroutine<ByteBuffer, String> fileRead =
			first(readFrom("test.file")).then(
				apply(bb -> StandardCharsets.UTF_8.decode(bb).toString()));

		ByteBuffer aWriteData =
			ByteBuffer.wrap(
				("Lorem ipsum dolor sit amet, consectetuer adipiscing elit, " +
					"sed diem nonummy nibh euismod tincidunt ut lacreet dolore " +
					"magna aliguam erat volutpat. Ut wisis enim ad minim veniam, " +
					"quis nostrud exerci tution ullamcorper suscipit lobortis " +
					"nisl ut aliquip ex ea commodo consequat.\n").getBytes());

		launch(run -> { run.async(fileWrite, aWriteData); });

		// separate scope to wait until fully written
		launch(
			run ->
			{
				ByteBuffer aReadData = ByteBuffer.allocate(10_000);

				Continuation<String> c = run.async(fileRead, aReadData);

				System.out.printf("READ: \n%s\n", c.getResult());
			});
	}

	/***************************************
	 * Runs coroutines parallel in threads and with asynchronous execution for
	 * comparison.
	 */
	public static void demoParallelExecution()
	{
		Coroutine<Object, Void> cr =
			first(run(() -> from(1).to(10).forEach(Math::sqrt)));

		int nThreadCount    = 100_000;
		int nCoroutineCount = 100_000;

		Profiler	   p	  = new Profiler("Parallel Coroutine Execution");
		CountDownLatch signal = new CountDownLatch(nThreadCount);

		for (int i = 0; i < nThreadCount; i++)
		{
			new Thread(
				() ->
			{
				launch(run ->
				{
					run.blocking(cr);
					signal.countDown();
				});
			}).start();
		}

		try
		{
			signal.await();
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}

		p.measure(nThreadCount + " Threads");

		launch(
			run ->
		{
			for (int i = 0; i < nCoroutineCount; i++)
			{
				run.async(cr);
			}
		});

		p.measure(nCoroutineCount + " Coroutines");
		p.printSummary();
	}

	/***************************************
	 * A demo of asynchronous socket communication based on Jav NIO.
	 */
	public static void demoSocketCommunication()
	{
		InetSocketAddress server = new InetSocketAddress("httpbin.org", 80);

		Coroutine<ByteBuffer, ByteBuffer> cr =
			first(sendTo(server)).then(receiveUntil(contentFullyRead()));

		launch(
			run ->
			{
				ByteBuffer data = ByteBuffer.allocate(100_000);

				data.put(
					"GET /get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n".getBytes(
						StandardCharsets.US_ASCII));

				data.flip();

				ByteBuffer result = run.async(cr, data).getResult();

				System.out.printf(
					"RESPONSE: \n%s",
					StandardCharsets.UTF_8.decode(result).toString());
			});
	}

	/***************************************
	 * Main
	 *
	 * @param rArgs
	 */
	public static void main(String[] rArgs)
	{
//		demoParallelExecution();
//		demoSocketCommunication();
//		demoFileIO();
	}
}
