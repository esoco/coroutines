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
package de.esoco.coroutine.step.nio;

import de.esoco.coroutine.Continuation;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;


/********************************************************************
 * Implements asynchronous writing to a {@link AsynchronousFileChannel}.
 *
 * @author eso
 */
public class FileWrite extends AsynchronousFileStep
{
	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fGetFileChannel A function that provides the file channel from the
	 *                        current continuation
	 */
	public FileWrite(
		Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel)
	{
		super(fGetFileChannel);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until all data from the input {@link ByteBuffer} has been
	 * written to a file.The buffer must be initialized for sending, i.e. if
	 * necessary a call to {@link Buffer#flip()} must have been performed.
	 *
	 * <p>After the data has been fully written {@link ByteBuffer#clear()} will
	 * be invoked on the buffer so that it can be used directly for subsequent
	 * writing to it.</p>
	 *
	 * @param  fGetFileChannel A function that provides the file channel from
	 *                         the current continuation
	 *
	 * @return A new step instance
	 */
	public static FileWrite writeTo(
		Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel)
	{
		return new FileWrite(fGetFileChannel);
	}

	/***************************************
	 * Invokes {@link #writeTo(Function)} with a function that opens a file
	 * channel with the given file name and options. The option {@link
	 * StandardOpenOption#WRITE} will always be used and should therefore not
	 * occur in the extra options.
	 *
	 * @param  sFileName     The name of the file to read from
	 * @param  rExtraOptions Additional options to use besides {@link
	 *                       StandardOpenOption#WRITE}
	 *
	 * @return A new step instance
	 */
	public static FileWrite writeTo(
		String		  sFileName,
		OpenOption... rExtraOptions)
	{
		return writeTo(
			c -> openFileChannel(
					sFileName,
					StandardOpenOption.WRITE,
					rExtraOptions));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected boolean performAsyncOperation(
		int												  nBytesWritten,
		AsynchronousFileChannel							  rChannel,
		ByteBuffer										  rData,
		ChannelCallback<Integer, AsynchronousFileChannel> rCallback)
	{
		long nPosition = get(FILE_POSITION);

		if (rData.hasRemaining())
		{
			if (nBytesWritten > 0)
			{
				nPosition += nBytesWritten;
			}

			rChannel.write(rData, nPosition, rData, rCallback);

			return false;
		}
		else // finished
		{
			// remove position in the case of a later restart
			deleteRelation(FILE_POSITION);
			rData.clear();

			return true;
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected void performBlockingOperation(
		AsynchronousFileChannel aChannel,
		ByteBuffer				rData) throws InterruptedException,
											  ExecutionException
	{
		long nPosition = 0;

		while (rData.hasRemaining())
		{
			nPosition += aChannel.write(rData, nPosition).get();
		}

		rData.clear();
	}
}
