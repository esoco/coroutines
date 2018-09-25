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

import de.esoco.coroutine.step.CodeExecution;

import java.util.concurrent.CompletableFuture;


/********************************************************************
 * This is the base class for all execution steps of coroutines. For simple
 * steps it is sufficient to implement the single abstract method {@link
 * #execute(Object, Continuation)} which must perform the actual code execution.
 * The default implementations of {@link #runBlocking(Object, Continuation)} and
 * {@link #runAsync(CompletableFuture, CoroutineStep, Continuation)} then invoke
 * this method as needed.
 *
 * <p>In most cases it is not necessary to subclass this class because the
 * 'step' sub-package already contains implementations of commons steps. For
 * example, a simple code execution can be achieved by putting a closure in to
 * an instance of the {@link CodeExecution} step.</p>
 *
 * <p>Creating a new step subclass is only needed to implement advanced
 * coroutine suspensions that are not already provided by existing steps. In
 * such a case it is typically also necessary to override the method {@link
 * #runAsync(CompletableFuture, CoroutineStep, Continuation)} to check for the
 * suspension condition. If a suspension is necessary a {@link Suspension}
 * object can be created by invoking a {@link #suspend(Object, Continuation)}
 * method. The object can then be used by code that waits for some external
 * condition to resume the coroutine when appropriate.</p>
 *
 * <p>It is recommended that a step implementation provides one or more static
 * factory methods alongside the constructor(s). These factory methods can then
 * be used as static imports for the fluent builder API of coroutines.</p>
 *
 * @author eso
 */
public abstract class CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	String sName;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 */
	protected CoroutineStep()
	{
		sName = getClass().getSimpleName();
	}

	/***************************************
	 * Creates a new instance with a certain name. This constructor can be used
	 * by subclasses that want to set another step name than the default (which
	 * is the class name without package).
	 *
	 * @param sName A label that identifies this step in it's coroutine
	 */
	protected CoroutineStep(String sName)
	{
		this.sName = sName;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Runs this execution step asynchronously as a continuation of a previous
	 * code execution in a {@link CompletableFuture} and proceeds to the next
	 * step afterwards.
	 *
	 * <p>Subclasses that need to suspend the invocation of the next step until
	 * some condition is met (e.g. sending or receiving data has finished) need
	 * to override this method and call {@link #resume(Object, Continuation)} on
	 * the next step if the suspension ends.</p>
	 *
	 * <p>Subclasses that override this method also need to handle errors by
	 * forwarding any exceptions to {@link Continuation#fail(Throwable)}.</p>
	 *
	 * @param fPreviousExecution The future of the previous code execution
	 * @param rNextStep          The next step to execute or NULL for none
	 * @param rContinuation      The continuation of the execution
	 */
	public void runAsync(CompletableFuture<I> fPreviousExecution,
						 CoroutineStep<O, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		CompletableFuture<O> fExecution =
			fPreviousExecution.thenApplyAsync(
				i -> execute(i, rContinuation),
				rContinuation);

		if (rNextStep != null)
		{
			// the next step is either a StepChain which contains it's own
			// next step or the final step in a coroutine and therefore the
			// rNextStep argument can be NULL
			rNextStep.runAsync(fExecution, null, rContinuation);
		}
		else
		{
			// only add exception handler to the end of a chain, i.e. next == null
			fExecution.exceptionally(e -> fail(e, rContinuation));
		}
	}

	/***************************************
	 * Runs this execution immediately, blocking the current thread until the
	 * execution finishes.
	 *
	 * @param  rInput        The input value
	 * @param  rContinuation The continuation of the execution
	 *
	 * @return The execution result
	 */
	public O runBlocking(I rInput, Continuation<?> rContinuation)
	{
		return execute(rInput, rContinuation);
	}

	/***************************************
	 * Suspends this step for later invocation and returns an instance of {@link
	 * Suspension} that contains the state necessary for resuming the execution.
	 * Other than {@link #suspend(Object, Continuation)} this suspension will
	 * not contain an explicit input value. Such suspensions are used if the
	 * input will only become available when the suspension ends (e.g. when
	 * receiving data asynchronously).
	 *
	 * @param  rContinuation The continuation of the suspended execution
	 *
	 * @return A new suspension object
	 */
	public Suspension<I> suspend(Continuation<?> rContinuation)
	{
		return suspend(null, rContinuation);
	}

	/***************************************
	 * Suspends this step for later invocation and returns an instance of {@link
	 * Suspension} that contains the state necessary for resuming the execution.
	 * If the input value is not known before the suspension ends the method
	 * {@link #suspend(Continuation)} can be used instead.
	 *
	 * @param  rInput        The input value for the execution
	 * @param  rContinuation The continuation of the suspended execution
	 *
	 * @return A new suspension object
	 */
	public Suspension<I> suspend(I rInput, Continuation<?> rContinuation)
	{
		return new Suspension<>(rInput, this, rContinuation);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return sName;
	}

	/***************************************
	 * This method must be implemented by subclasses to provide the actual
	 * functionality of this step.
	 *
	 * @param  rInput        The input value
	 * @param  rContinuation The continuation of the execution
	 *
	 * @return The result of the execution
	 */
	protected abstract O execute(I rInput, Continuation<?> rContinuation);

	/***************************************
	 * Signals an execution failure to the given continuation. This method
	 * should be invoked by subclasses the encounter an exception during
	 * execution. The return value is always NULL but the method signature is
	 * suitable to be used for invocation in a lambda expression as the argument
	 * to {@link CompletableFuture#exceptionally(java.util.function.Function)}.
	 *
	 * @param  eError        The exception that occurred
	 * @param  rContinuation The continuation
	 *
	 * @return Always NULL, but with the type of the coroutine result
	 */
	protected O fail(Throwable eError, Continuation<?> rContinuation)
	{
		rContinuation.fail(eError);

		return null;
	}

	/***************************************
	 * Allows subclasses to regularly terminate the coroutine that is executed
	 * in the given continuation with a result of NULL.
	 *
	 * @param rContinuation The continuation to finish
	 */
	protected void terminateCoroutine(Continuation<?> rContinuation)
	{
		rContinuation.finish(null);
	}
}
