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
package de.esoco.coroutine.step;

import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineException;
import de.esoco.coroutine.CoroutineStep;

import de.esoco.lib.datatype.Pair;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/********************************************************************
 * A suspending {@link Coroutine} step that performs delayed executions.
 *
 * @author eso
 */
public class Delay<T> extends CoroutineStep<T, T>
{
	//~ Instance fields --------------------------------------------------------

	private Function<Continuation<?>, Pair<Long, TimeUnit>> fGetDuration;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fGetDuration A function that determines the duration to sleep from
	 *                     the continuation
	 */
	public Delay(Function<Continuation<?>, Pair<Long, TimeUnit>> fGetDuration)
	{
		this.fGetDuration = fGetDuration;
		Objects.requireNonNull(fGetDuration);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends the coroutine execution for a certain duration in milliseconds.
	 *
	 * @param nMilliseconds The milliseconds to sleep
	 *
	 * @see   #sleep(long, TimeUnit)
	 */
	public static <T> Delay<T> sleep(long nMilliseconds)
	{
		return sleep(nMilliseconds, TimeUnit.MILLISECONDS);
	}

	/***************************************
	 * Suspends the coroutine execution for a duration stored in a certain state
	 * relation. The lookup of the duration value follows the rules defined by
	 * {@link Continuation#getState(RelationType, Object)}.
	 *
	 * @param  fGetDuration A function that determines the duration to sleep
	 *                      from the continuation
	 *
	 * @return A new step instance
	 */
	public static <T> Delay<T> sleep(
		Function<Continuation<?>, Pair<Long, TimeUnit>> fGetDuration)
	{
		return new Delay<>(fGetDuration);
	}

	/***************************************
	 * Suspends the coroutine execution for a certain duration.
	 *
	 * @param  nDuration The duration to sleep
	 * @param  eUnit     The time unit of the duration
	 *
	 * @return A new step instance
	 */
	public static <T> Delay<T> sleep(long nDuration, TimeUnit eUnit)
	{
		return new Delay<>(c -> Pair.of(nDuration, eUnit));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public T execute(T rInput, Continuation<?> rContinuation)
	{
		try
		{
			Pair<Long, TimeUnit> rDuration = fGetDuration.apply(rContinuation);

			rDuration.second().sleep(rDuration.first());
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}

		return rInput;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<T> fPreviousExecution,
						 CoroutineStep<T, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
				  			v ->
				  			{
				  				Pair<Long, TimeUnit> rDuration =
				  					fGetDuration.apply(rContinuation);

				  				rContinuation.context()
				  				.getScheduler()
				  				.schedule(
				  					() ->
				  						rContinuation.suspend(
				  							this,
				  							rNextStep)
				  						.resume(v),
				  					rDuration.first(),
				  					rDuration.second());
				  			},
				  			rContinuation)
						  .exceptionally(t ->
				  				rContinuation.fail(t));
	}
}
