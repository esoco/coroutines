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

import java.net.SocketAddress;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;


/********************************************************************
 * Implements asynchronous writing to a {@link AsynchronousSocketChannel}.
 *
 * @author eso
 */
public class SocketSend extends AsynchronousSocketStep
{
	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fGetSocketAddress A function that provides the target socket
	 *                          address from the current continuation
	 */
	public SocketSend(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress)
	{
		super(fGetSocketAddress);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until all data from the input {@link ByteBuffer} has been sent
	 * to a network socket.The buffer must be initialized for sending, i.e. if
	 * necessary a call to {@link Buffer#flip()} must have been performed.
	 *
	 * <p>After the data has been fully sent {@link ByteBuffer#clear()} will be
	 * invoked on the buffer so that it can be used directly for subsequent
	 * writing to it. An example would be a following {@link SocketReceive} to
	 * implement a request-response scheme.</p>
	 *
	 * @param  fGetSocketAddress A function that provides the target socket
	 *                           address from the current continuation
	 *
	 * @return A new step instance
	 */
	public static SocketSend sendTo(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress)
	{
		return new SocketSend(fGetSocketAddress);
	}

	/***************************************
	 * @see #sendTo(Function)
	 */
	public static SocketSend sendTo(SocketAddress rSocketAddress)
	{
		return sendTo(c -> rSocketAddress);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected boolean performAsyncOperation(
		int													nBytesProcessed,
		AsynchronousSocketChannel							rChannel,
		ByteBuffer											rData,
		ChannelCallback<Integer, AsynchronousSocketChannel> rCallback)
	{
		if (rData.hasRemaining())
		{
			rChannel.write(rData, rData, rCallback);

			return false;
		}
		else
		{
			rData.clear();

			return true;
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected void performBlockingOperation(
		AsynchronousSocketChannel aChannel,
		ByteBuffer				  rData) throws InterruptedException,
												ExecutionException
	{
		while (rData.hasRemaining())
		{
			aChannel.write(rData).get();
		}

		rData.clear();
	}
}
