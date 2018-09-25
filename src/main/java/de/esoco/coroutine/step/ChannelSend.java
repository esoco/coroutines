//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-lib' project.
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
package de.esoco.coroutine.step;

import de.esoco.coroutine.ChannelId;
import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.CoroutineStep;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;


/********************************************************************
 * A coroutine step that sends a value into a channel. If the channel capacity
 * has been reached at the time of this step's invocation the coroutine
 * execution will be suspended until channel capacity becomes available.
 *
 * <p>A send step returns the input value it sends so that it ca be processed
 * further in subsequent steps if needed.</p>
 *
 * @author eso
 */
public class ChannelSend<T> extends CoroutineStep<T, T>
{
	//~ Instance fields --------------------------------------------------------

	private ChannelId<T> rChannelId;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rId The ID of the channel to send to
	 */
	public ChannelSend(ChannelId<T> rId)
	{
		Objects.requireNonNull(rId);
		this.rChannelId = rId;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until a value can be sent to a channel.
	 *
	 * @param  rId The ID of the channel to send to
	 *
	 * @return A new instance of this class
	 */
	public static <T> ChannelSend<T> send(ChannelId<T> rId)
	{
		return new ChannelSend<>(rId);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<T> fPreviousExecution,
						 CoroutineStep<T, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
			v -> rContinuation.getChannel(rChannelId)
				.sendSuspending(rNextStep.suspend(v, rContinuation)),
			rContinuation);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected T execute(T rInput, Continuation<?> rContinuation)
	{
		rContinuation.getChannel(rChannelId).sendBlocking(rInput);

		return rInput;
	}
}
