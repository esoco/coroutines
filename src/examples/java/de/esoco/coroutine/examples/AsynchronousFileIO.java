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

import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.step.nio.FileRead;
import de.esoco.coroutine.step.nio.FileWrite;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.nio.FileRead.readFrom;
import static de.esoco.coroutine.step.nio.FileWrite.writeTo;


/********************************************************************
 * Example of asynchronous file I/O in coroutines, with the java.nio-based steps
 * {@link FileWrite} and {@link FileRead}.
 *
 * @author eso
 */
public class AsynchronousFileIO
{
	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Main
	 *
	 * @param rArgs
	 */
	public static void main(String[] rArgs)
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

		launch(scope -> { fileWrite.runAsync(scope, aWriteData); });

		// separate scope to wait until write is complete
		// Continuation.await() in the same scope would also be possible
		launch(
			scope ->
			{
				ByteBuffer aReadData = ByteBuffer.allocate(10_000);

				Continuation<String> c = fileRead.runAsync(scope, aReadData);

				System.out.printf("READ: \n%s\n", c.getResult());
			});
	}
}
