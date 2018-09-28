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

import java.io.EOFException;

import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import java.util.concurrent.CompletionException;
import java.util.function.Function;


/********************************************************************
 * Implements asynchronous writing to a {@link AsynchronousSocketChannel}.
 *
 * @author eso
 */
public class SocketReceive extends AsynchronousSocketStep
{
	//~ Instance fields --------------------------------------------------------

	private boolean bUntilEnd;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fGetSocketAddress A function that provides the target socket
	 *                          address from the current continuation
	 * @param bUntilEnd         TRUE to read data until an end-of-stream signal
	 *                          (-1) is received or FALSE to only read data once
	 */
	public SocketReceive(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress,
		boolean									 bUntilEnd)
	{
		super(fGetSocketAddress);

		this.bUntilEnd = bUntilEnd;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until all available data has been received from a network
	 * socket. The data will be stored in the input {@link ByteBuffer} of the
	 * step. If the capacity of the buffer is reached before the EOF signal is
	 * received the coroutine will be terminated with a {@link
	 * CompletionException}.
	 *
	 * <p>This variant receives data until an end-of-stream signal (size of -1)
	 * is received. If no such signal is available from the socket the step
	 * needs to be created with {@link #receiveFrom(Function)}.</p>
	 *
	 * @param  fGetSocketAddress A function that provides the source socket
	 *                           address from the current continuation
	 *
	 * @return A new step instance
	 */
	public static SocketReceive receiveAllFrom(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress)
	{
		return new SocketReceive(fGetSocketAddress, true);
	}

	/***************************************
	 * @see #receiveFrom(Function)
	 */
	public static SocketReceive receiveAllFrom(SocketAddress rSocketAddress)
	{
		return receiveAllFrom(c -> rSocketAddress);
	}

	/***************************************
	 * Suspends until data has been received from a network socket. The data
	 * will be stored in the input {@link ByteBuffer} of the step. If the
	 * capacity of the buffer is reached before the EOF signal is received the
	 * coroutine will be terminated with a {@link CompletionException}.
	 *
	 * <p>This variant only receives the next block of data that is sent by the
	 * remote socket and then continues execution. If data should be read until
	 * an end-of-stream signal is received the step needs to be created with
	 * {@link #receiveAllFrom(Function)}.</p>
	 *
	 * @param  fGetSocketAddress A function that provides the source socket
	 *                           address from the current continuation
	 *
	 * @return A new step instance
	 */
	public static SocketReceive receiveFrom(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress)
	{
		return new SocketReceive(fGetSocketAddress, false);
	}

	/***************************************
	 * @see #receiveFrom(Function)
	 */
	public static SocketReceive receiveFrom(SocketAddress rSocketAddress)
	{
		return receiveFrom(c -> rSocketAddress);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected boolean performAsyncOperation(
		int													nReceived,
		AsynchronousSocketChannel							rChannel,
		ByteBuffer											rData,
		ChannelCallback<Integer, AsynchronousSocketChannel> rCallback)
	{
		if ((nReceived == FIRST_OPERATION || (bUntilEnd && nReceived > 0)) &&
			rData.hasRemaining())
		{
			rChannel.read(rData, rData, rCallback);

			return false;
		}
		else
		{
			return true;
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected void performBlockingOperation(
		AsynchronousSocketChannel aChannel,
		ByteBuffer				  rData) throws Exception
	{
		int nReceived;

		do
		{
			nReceived = aChannel.read(rData).get();
		}
		while (bUntilEnd && nReceived != -1 && rData.hasRemaining());

		if (bUntilEnd && nReceived != -1)
		{
			throw new EOFException("Buffer size to small");
		}
	}
}
