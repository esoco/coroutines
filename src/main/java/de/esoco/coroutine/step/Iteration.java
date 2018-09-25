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

import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.CoroutineStep;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static de.esoco.coroutine.step.CodeExecution.consume;


/********************************************************************
 * A step that implements suspendable iteration over an {@link Iterable} input
 * value. Each value returned by the iterator will be processed with a separate
 * execution, allowing steps from other coroutines to run in parallel. The
 * processed values can either be discarded or collected for further processing.
 * The static factory methods {@link #forEach(CoroutineStep)} and {@link
 * #forEach(CoroutineStep, Supplier)} create instances for both scenarios.
 *
 * @author eso
 */
public class Iteration<T, R, I extends Iterable<T>, C extends Collection<R>>
	extends CoroutineStep<I, C>
{
	//~ Instance fields --------------------------------------------------------

	private final CoroutineStep<T, R> rProcessingStep;
	private final Supplier<C>		  fCollectionFactory;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rProcessingStep    The step to be applied to each value returned
	 *                           by the iterator
	 * @param fCollectionFactory A supplier that returns a collection of the
	 *                           target type to store the processed values in
	 */
	public Iteration(
		CoroutineStep<T, R> rProcessingStep,
		Supplier<C>			fCollectionFactory)
	{
		this.rProcessingStep    = rProcessingStep;
		this.fCollectionFactory = fCollectionFactory;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Iterates over each element in the {@link Iterator} of an {@link Iterable}
	 * input value and processes the element with another step. The processed
	 * values will be discarded, the returned step will always have a result of
	 * NULL. The method {@link #forEach(CoroutineStep, Supplier)} can be used to
	 * collect the processed values.
	 *
	 * @param  rProcessingStep The step to process each value
	 *
	 * @return A new step instance
	 */
	public static <T, R, I extends Collection<T>, C extends Collection<R>> Iteration<T, R,
																					 I, C>
	forEach(CoroutineStep<T, R> rProcessingStep)
	{
		return new Iteration<>(rProcessingStep, null);
	}

	/***************************************
	 * Iterates over each element in the {@link Iterator} of an {@link Iterable}
	 * input value, processes the element with another step, and collects the
	 * result into a target collection. If invoked asynchronously each iteration
	 * step will be invoked as a separate suspension, but sequentially for each
	 * value returned by the iterator. After the iteration has completed the
	 * coroutine continues with the next step with the collected values as it's
	 * input.
	 *
	 * @param  rProcessingStep    The step to process each value
	 * @param  fCollectionFactory A supplier that returns a collection of the
	 *                            target type to store the processed values in
	 *
	 * @return A new step instance
	 */
	public static <T, R, I extends Iterable<T>, C extends Collection<R>> Iteration<T, R,
																				   I, C>
	forEach(CoroutineStep<T, R> rProcessingStep, Supplier<C> fCollectionFactory)
	{
		return new Iteration<>(rProcessingStep, fCollectionFactory);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<I> fPreviousExecution,
						 CoroutineStep<C, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		C aResults =
			fCollectionFactory != null ? fCollectionFactory.get() : null;

		fPreviousExecution.thenAcceptAsync(
			i -> iterateAsync(i.iterator(), aResults, rNextStep, rContinuation));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected C execute(I rInput, Continuation<?> rContinuation)
	{
		C aResults =
			fCollectionFactory != null ? fCollectionFactory.get() : null;

		for (T rValue : rInput)
		{
			R aResult = rProcessingStep.runBlocking(rValue, rContinuation);

			if (aResults != null)
			{
				aResults.add(aResult);
			}
		}

		return aResults;
	}

	/***************************************
	 * Performs the asynchronous iteration over all values in an iterator.
	 *
	 * @param rIterator     The iterator
	 * @param rResults      The collection to place the processed values in or
	 *                      NULL to collect no results
	 * @param rNextStep     The step to execute when the iteration is finished
	 * @param rContinuation The current continuation
	 */
	private void iterateAsync(Iterator<T>		  rIterator,
							  C					  rResults,
							  CoroutineStep<C, ?> rNextStep,
							  Continuation<?>	  rContinuation)
	{
		if (rIterator.hasNext())
		{
			CompletableFuture<T> fGetNextValue =
				CompletableFuture.supplyAsync(
					() -> rIterator.next(),
					rContinuation);

			rProcessingStep.runAsync(
				fGetNextValue,
				consume(
					o ->
					{
						if (rResults != null)
						{
							rResults.add(o);
						}

						iterateAsync(
							rIterator,
							rResults,
							rNextStep,
							rContinuation);
					}),
				rContinuation);
		}
		else
		{
			rNextStep.suspend(rContinuation).resume(rResults);
		}
	}
}
