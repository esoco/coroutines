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
package de.esoco.coroutine.examples;

import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.step.nio.SocketReceive;
import de.esoco.coroutine.step.nio.SocketSend;

import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.nio.SocketReceive.contentFullyRead;
import static de.esoco.coroutine.step.nio.SocketReceive.receiveUntil;
import static de.esoco.coroutine.step.nio.SocketSend.sendTo;


/********************************************************************
 * Example of asynchronous socket I/O in coroutines, with the java.nio-based
 * steps {@link SocketSend} and {@link SocketReceive}.
 *
 * @author eso
 */
public class AsynchronousSocketIO
{
	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Main
	 *
	 * @param rArgs
	 */
	public static void main(String[] rArgs)
	{
		InetSocketAddress server = new InetSocketAddress("httpbin.org", 80);

		Coroutine<ByteBuffer, ByteBuffer> requestResponse =
			first(sendTo(server)).then(receiveUntil(contentFullyRead()));

		launch(
			scope ->
			{
				ByteBuffer data = ByteBuffer.allocate(100_000);

				data.put(
					"GET /get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n".getBytes(
						StandardCharsets.US_ASCII));

				data.flip();

				ByteBuffer result =
					requestResponse.runAsync(scope, data).getResult();

				System.out.printf(
					"RESPONSE: \n%s",
					StandardCharsets.UTF_8.decode(result).toString());
			});
	}
}
