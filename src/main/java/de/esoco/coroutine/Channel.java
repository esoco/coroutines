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
package de.esoco.coroutine;

import de.esoco.lib.concurrent.RunLock;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;


/********************************************************************
 * A channel that allows communication between {@link Coroutine Coroutines}.
 *
 * @author eso
 */
public class Channel<T>
{
	//~ Instance fields --------------------------------------------------------

	private final ChannelId<T> rId;

	private final BlockingQueue<T>     aChannelData;
	private final Deque<Suspension<T>> aSendQueue    = new LinkedList<>();
	private final Deque<Suspension<T>> aReceiveQueue = new LinkedList<>();

	private final RunLock aAccessLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rId       The channel identifier
	 * @param nCapacity The maximum number of values the channel can hold before
	 *                  blocking
	 */
	protected Channel(ChannelId<T> rId, int nCapacity)
	{
		this.rId = rId;

		aChannelData = new LinkedBlockingQueue<>(nCapacity);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Returns the channel identifier.
	 *
	 * @return The channel ID
	 */
	public ChannelId<T> getId()
	{
		return rId;
	}

	/***************************************
	 * Receives a value from this channel, blocking if no data is available.
	 *
	 * @return The next value from this channel or NULL if the waiting for a
	 *         value has been interrupted
	 */
	public T receiveBlocking()
	{
		return aAccessLock.supplyLocked(
			() ->
			{
				try
				{
					T rValue = aChannelData.take();

					resumeSenders();

					return rValue;
				}
				catch (InterruptedException e)
				{
					throw new CompletionException(e);
				}
			});
	}

	/***************************************
	 * Tries to receive a value from this channel and resumes the execution of a
	 * {@link Coroutine} at the given suspension as soon as a value becomes
	 * available. This can be immediately or, if the channel is empty, only
	 * after some other code sends a values into this channel. Suspended senders
	 * will be served with a first-suspended-first-served policy.
	 *
	 * @param rSuspension The coroutine suspension to resume after data has been
	 *                    receive
	 */
	public void receiveSuspending(Suspension<T> rSuspension)
	{
		aAccessLock.runLocked(
			() ->
			{
				T rValue = aChannelData.poll();

				if (rValue != null)
				{
					rSuspension.resume(rValue);
					resumeSenders();
				}
				else
				{
					aReceiveQueue.add(rSuspension);
				}
			});
	}

	/***************************************
	 * Returns the number of values that can still be send to this channel. Due
	 * to the concurrent nature of channels this can only be a momentary value
	 * which needs to be interpreted with caution and necessary synchronization
	 * should be performed if applicable. Concurrently running coroutines could
	 * affect this value at any time.
	 *
	 * @return The remaining channel capacity
	 */
	public int remainingCapacity()
	{
		return aChannelData.remainingCapacity();
	}

	/***************************************
	 * Sends a value into this channel, blocking if no capacity is available.
	 *
	 * @param rValue The value to send
	 */
	public void sendBlocking(T rValue)
	{
		aAccessLock.runLocked(
			() ->
			{
				try
				{
					aChannelData.put(rValue);
					resumeReceivers();
				}
				catch (InterruptedException e)
				{
					throw new CompletionException(e);
				}
			});
	}

	/***************************************
	 * Tries to send a value into this channel and resumes the execution of a
	 * {@link Coroutine} at the given step as soon as channel capacity becomes
	 * available. This can be immediately or, if the channel is full, only after
	 * some other code receives a values from this channel. Suspended senders
	 * will be served with a first-suspended-first-served policy.
	 *
	 * @param rSuspension rValue The value to send
	 */
	public void sendSuspending(Suspension<T> rSuspension)
	{
		aAccessLock.runLocked(
			() ->
			{
				if (aChannelData.offer(rSuspension.input()))
				{
					rSuspension.resume();
					resumeReceivers();
				}
				else
				{
					aSendQueue.add(rSuspension);
				}
			});
	}

	/***************************************
	 * Returns the current number of values in this channel. Due to the
	 * concurrent nature of channels this can only be a momentary value which
	 * needs to be interpreted with caution and necessary synchronization should
	 * be performed if applicable. Concurrently running coroutines could affect
	 * this value at any time.
	 *
	 * @return The current number of channel entries
	 */
	public int size()
	{
		return aChannelData.size();
	}

	/***************************************
	 * Notifies suspended receivers that new data has become available in this
	 * channel.
	 */
	private void resumeReceivers()
	{
		while (aChannelData.size() > 0 && !aReceiveQueue.isEmpty())
		{
			Suspension<T> rSuspension = aReceiveQueue.remove();

			T rValue = aChannelData.remove();

			if (rValue != null)
			{
				rSuspension.resume(rValue);
			}
			else
			{
				aReceiveQueue.push(rSuspension);
			}
		}
	}

	/***************************************
	 * Notifies suspended senders that channel capacity has become available.
	 */
	private void resumeSenders()
	{
		while (aChannelData.remainingCapacity() > 0 && !aSendQueue.isEmpty())
		{
			Suspension<T> rSuspension = aSendQueue.remove();

			if (aChannelData.offer(rSuspension.input()))
			{
				rSuspension.resume();
			}
			else
			{
				aSendQueue.push(rSuspension);
			}
		}
	}
}
