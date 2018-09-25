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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.obrel.core.RelatedObject;


/********************************************************************
 * The context for the execution of {@link Coroutine Coroutines}.
 *
 * @author eso
 */
public class CoroutineContext extends RelatedObject
{
	//~ Instance fields --------------------------------------------------------

	private final Executor			 rExecutor;
	private ScheduledExecutorService rScheduler;

	private final Map<ChannelId<?>, Channel<?>> aChannels    = new HashMap<>();
	private final RunLock					    aChannelLock = new RunLock();

	private final AtomicLong nRunningScopes		   = new AtomicLong();
	private final RunLock    aScopeLock			   = new RunLock();
	private CountDownLatch   aScopesFinishedSignal = new CountDownLatch(1);

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that uses the {@link ForkJoinPool#commonPool()
	 * common thread pool} as the executor.
	 */
	public CoroutineContext()
	{
		this(ForkJoinPool.commonPool());
	}

	/***************************************
	 * Creates a new instance with a specific coroutine executor. If the
	 * executor also implements the {@link ScheduledExecutorService} interface
	 * it will also be used for scheduling purposes.
	 *
	 * @param rExecutor The coroutine executor
	 */
	public CoroutineContext(Executor rExecutor)
	{
		this.rExecutor = rExecutor;

		if (rExecutor instanceof ScheduledExecutorService)
		{
			rScheduler = (ScheduledExecutorService) rExecutor;
		}
	}

	/***************************************
	 * Creates a new instance with a specific coroutine executor and scheduler.
	 * The latter will be used to execute timed coroutine steps.
	 *
	 * @param rExecutor  The coroutine executor
	 * @param rScheduler The scheduled executor service
	 */
	public CoroutineContext(
		Executor				 rExecutor,
		ScheduledExecutorService rScheduler)
	{
		this.rExecutor  = rExecutor;
		this.rScheduler = rScheduler;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Blocks until the coroutines of all {@link CoroutineScope scopes} in this
	 * context have finished execution. If no coroutines are running or all have
	 * finished execution already this method returns immediately.
	 */
	public void awaitAllScopes()
	{
		if (aScopesFinishedSignal != null)
		{
			try
			{
				aScopesFinishedSignal.await();
			}
			catch (InterruptedException e)
			{
				throw new CompletionException(e);
			}
		}
	}

	/***************************************
	 * Creates a new channel with a certain capacity and stores it in this
	 * context for lookup with {@link #getChannel(ChannelId)}. If a channel with
	 * the given ID already exists an exception will be thrown to prevent
	 * accidental overwriting of channels.
	 *
	 * @param  rId       The channel ID
	 * @param  nCapacity The channel capacity
	 *
	 * @return The new channel
	 *
	 * @throws IllegalArgumentException If a channel with the given ID already
	 *                                  exists
	 */
	public <T> Channel<T> createChannel(ChannelId<T> rId, int nCapacity)
	{
		return aChannelLock.supplyLocked(
			() ->
			{
				if (aChannels.containsKey(rId))
				{
					throw new IllegalArgumentException(
						String.format("Channel %s already exists", rId));
				}

				Channel<T> aChannel = new Channel<>(rId, nCapacity);

				aChannels.put(rId, aChannel);

				return aChannel;
			});
	}

	/***************************************
	 * Returns a channel for a certain ID. If no such channel exists a new
	 * channel with a capacity of 1 (one) entry will be created and stored under
	 * the given ID. To prevent this the channel needs to be created in advance
	 * by calling {@link #createChannel(ChannelId, int)}.
	 *
	 * @param  rId The channel ID
	 *
	 * @return The channel for the given ID
	 */
	@SuppressWarnings("unchecked")
	public <T> Channel<T> getChannel(ChannelId<T> rId)
	{
		return aChannelLock.supplyLocked(
			() ->
			{
				Channel<T> rChannel = (Channel<T>) aChannels.get(rId);

				if (rChannel == null)
				{
					rChannel = createChannel(rId, 1);
				}

				return rChannel;
			});
	}

	/***************************************
	 * Returns the executor to be used for the execution of the steps of a
	 * {@link Coroutine}.
	 *
	 * @return The coroutine executor for this context
	 */
	public Executor getExecutor()
	{
		return rExecutor;
	}

	/***************************************
	 * Returns the executor to be used for the execution of timed steps in a
	 * {@link Coroutine}. If no scheduler has been set in the constructor or
	 * created before a new instance with a pool size of 1 will be created by
	 * invoking {@link Executors#newScheduledThreadPool(int)}.
	 *
	 * @return The coroutine scheduler for this context
	 */
	public ScheduledExecutorService getScheduler()
	{
		if (rScheduler == null)
		{
			rScheduler = Executors.newScheduledThreadPool(1);
		}

		return rScheduler;
	}

	/***************************************
	 * Returns the number of currently active {@link CoroutineScope scopes}.
	 * This will only be a momentary value as the execution of the coroutines in
	 * the scopes happens asynchronously and some coroutines may finish while
	 * querying this count.
	 *
	 * @return The number of running coroutines
	 */
	public long getScopeCount()
	{
		return nRunningScopes.get();
	}

	/***************************************
	 * Checks whether this context contains a certain channel. As with the other
	 * channel access methods the result depends on the current execution state
	 * of the coroutines in this context. It is recommended to only invoke this
	 * method if concurrent modification of the queries channel will not occur.
	 *
	 * @param  rId The channel ID
	 *
	 * @return TRUE if the channel exists in this context
	 */
	public boolean hasChannel(ChannelId<?> rId)
	{
		return aChannelLock.supplyLocked(() -> aChannels.containsKey(rId));
	}

	/***************************************
	 * Removes a channel from this context. This method should be applied with
	 * caution because concurrently running coroutines may try to access or
	 * event re-create the channel in parallel. It is recommended to invoke this
	 * method only on contexts without running coroutines that access the
	 * channel ID. Otherwise synchronization is necessary.
	 *
	 * @param rId The channel ID
	 */
	public void removeChannel(ChannelId<?> rId)
	{
		aChannelLock.runLocked(() -> aChannels.remove(rId));
	}

	/***************************************
	 * Notifies this context that a {@link CoroutineScope} has finished
	 * executing all coroutines.
	 *
	 * @param rScope The finished scope
	 */
	void scopeFinished(CoroutineScope rScope)
	{
		if (nRunningScopes.decrementAndGet() == 0)
		{
			aScopeLock.runLocked(() -> aScopesFinishedSignal.countDown());
		}
	}

	/***************************************
	 * Notifies this context that a {@link CoroutineScope} has been launched.
	 *
	 * @param rScope The launched scope
	 */
	void scopeLaunched(CoroutineScope rScope)
	{
		if (nRunningScopes.incrementAndGet() == 1)
		{
			aScopeLock.runLocked(
				() ->
			{
				if (aScopesFinishedSignal.getCount() == 0)
				{
					aScopesFinishedSignal = new CountDownLatch(1);
				}
			});
		}
	}
}
