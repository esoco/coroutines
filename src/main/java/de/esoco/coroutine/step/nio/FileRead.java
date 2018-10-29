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
import de.esoco.coroutine.CoroutineException;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

import java.util.function.BiPredicate;
import java.util.function.Function;


/********************************************************************
 * Implements asynchronous reading from a {@link AsynchronousFileChannel}.
 *
 * @author eso
 */
public class FileRead extends AsynchronousFileStep
{
	//~ Instance fields --------------------------------------------------------

	private final BiPredicate<Integer, ByteBuffer> pCheckFinished;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fGetFileChannel A function that provides the file channel from the
	 *                        current continuation
	 * @param pCheckFinished  A predicate that checks whether the operation is
	 *                        complete by evaluating the byte buffer after
	 *                        reading
	 */
	public FileRead(
		Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel,
		BiPredicate<Integer, ByteBuffer>				   pCheckFinished)
	{
		super(fGetFileChannel);

		this.pCheckFinished = pCheckFinished;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until a file has been read completely. The data will be stored
	 * in the input {@link ByteBuffer} of the step. If the capacity of the
	 * buffer is reached before the EOF signal is received the coroutine will be
	 * terminated with a {@link CoroutineException}. To stop reading when a
	 * certain condition is met a derived step can be created with {@link
	 * #until(BiPredicate)}.
	 *
	 * <p>After the data has been fully received {@link ByteBuffer#flip()} will
	 * be invoked on the buffer so that it can be used directly for subsequent
	 * reading from it.</p>
	 *
	 * @param  fGetFileChannel A function that provides the file channel from
	 *                         the current continuation
	 *
	 * @return A new step instance
	 */
	public static AsynchronousFileStep readFrom(
		Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel)
	{
		return new FileRead(fGetFileChannel, (r, bb) -> r != -1);
	}

	/***************************************
	 * Invokes {@link #readFrom(Function)} with a function that opens a file
	 * channel with the given file name and options. The option {@link
	 * StandardOpenOption#READ} will always be used and should therefore not
	 * occur in the extra options.
	 *
	 * @param  sFileName     The name of the file to read from
	 * @param  rExtraOptions Additional options to use besides {@link
	 *                       StandardOpenOption#READ}
	 *
	 * @return A new step instance
	 */
	public static AsynchronousFileStep readFrom(
		String		  sFileName,
		OpenOption... rExtraOptions)
	{
		return readFrom(
			c -> openFileChannel(
					sFileName,
					StandardOpenOption.READ,
					rExtraOptions));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Returns a new read step instance the suspends until data has been read
	 * from a file and a certain condition on that data is met or an
	 * end-of-stream signal is received. If the capacity of the buffer is
	 * reached before the receiving is finished the coroutine will fail with an
	 * exception.
	 *
	 * @param  pCheckFinished A predicate that checks whether the data has been
	 *                        read completely
	 *
	 * @return A new step instance
	 */
	public AsynchronousFileStep until(
		BiPredicate<Integer, ByteBuffer> pCheckFinished)
	{
		return new FileRead(getFileChannelFactory(), pCheckFinished);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected boolean performAsyncOperation(
		int												  nBytesRead,
		AsynchronousFileChannel							  rChannel,
		ByteBuffer										  rData,
		ChannelCallback<Integer, AsynchronousFileChannel> rCallback)
		throws IOException
	{
		long    nPosition = get(FILE_POSITION);
		boolean bFinished = false;

		if (nBytesRead >= 0)
		{
			bFinished =  pCheckFinished.test(nBytesRead, rData);
			nPosition += nBytesRead;
		}

		if (nBytesRead != -1 && !bFinished && rData.hasRemaining())
		{
			rChannel.read(rData, nPosition, rData, rCallback);
		}
		else // finished, either successfully or with an error
		{
			checkErrors(rData, nBytesRead, bFinished);

			// remove position in the case of a later restart
			deleteRelation(FILE_POSITION);
			rData.flip();
		}

		return bFinished;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected void performBlockingOperation(
		AsynchronousFileChannel aChannel,
		ByteBuffer				rData) throws Exception
	{
		long    nPosition  = 0;
		int     nBytesRead;
		boolean bFinished;

		do
		{
			nBytesRead = aChannel.read(rData, nPosition).get();
			bFinished  = pCheckFinished.test(nBytesRead, rData);

			if (nBytesRead > 0)
			{
				nPosition += nBytesRead;
			}
		}
		while (nBytesRead != -1 && !bFinished && rData.hasRemaining());

		checkErrors(rData, nBytesRead, bFinished);

		rData.flip();
	}

	/***************************************
	 * Checks the read data and throws an exception on errors.
	 *
	 * @param  rData      The received data bytes
	 * @param  nBytesRead The number of bytes in the last read
	 * @param  bFinished  TRUE if the finish condition is met
	 *
	 * @throws IOException If an error is detected
	 */
	private void checkErrors(ByteBuffer rData,
							 int		nBytesRead,
							 boolean    bFinished) throws IOException
	{
		if (!bFinished)
		{
			if (nBytesRead == -1)
			{
				throw new IOException("Received data incomplete");
			}
			else if (!rData.hasRemaining())
			{
				throw new IOException("Buffer capacity exceeded");
			}
		}
	}
}
