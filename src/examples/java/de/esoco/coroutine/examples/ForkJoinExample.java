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
package de.esoco.coroutine.examples;

import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineScope;
import de.esoco.coroutine.CoroutineScope.ScopeFuture;

import de.esoco.lib.logging.Profiler;

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.DoubleAdder;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;

import static de.esoco.coroutine.CoroutineScope.produce;
import static de.esoco.coroutine.step.CodeExecution.run;

import static org.obrel.core.RelationTypes.newInitialValueType;


/********************************************************************
 * An example of coroutine executions similar to {@link ForkJoinTask}. This
 * implementation is derived from <a
 * href="https://github.com/Kotlin/kotlinx.coroutines/blob/master/benchmarks/src/jmh/kotlin/benchmarks/ForkJoinBenchmark.kt">
 * the Kotlin Fork-Join benchmark</a>.
 *
 * @author eso
 */
public class ForkJoinExample
{
	//~ Static fields/initializers ---------------------------------------------

	private static final RelationType<DoubleAdder> RESULT =
		newInitialValueType(r -> new DoubleAdder());

	private static final int TASK_SIZE  = 8_192 * 1_024;
	private static final int BATCH_SIZE = 1_024;

	static
	{
		RelationTypes.init(ForkJoinExample.class);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Runs the benchmark.
	 *
	 * @param rArgs The arguments
	 */
	public static void main(String[] rArgs)
	{
		ThreadLocalRandom rRandom	    = ThreadLocalRandom.current();
		long[]			  aCoefficients = new long[TASK_SIZE];

		for (int i = 0; i < TASK_SIZE; i++)
		{
			aCoefficients[i] = rRandom.nextLong(0, 1024 * 1024);
		}

		Profiler aProfiler =
			new Profiler(ForkJoinExample.class.getSimpleName());

		ScopeFuture<DoubleAdder> aResult =
			produce(
				RESULT,
				scope -> computeAsync(scope, aCoefficients, 0, TASK_SIZE));

		System.out.printf("Sum: %f\n", aResult.get().sum());

		aProfiler.measure(
			String.format(
				"%d tasks\nwith a batch size of %d",
				TASK_SIZE,
				BATCH_SIZE));
		aProfiler.printSummary();
	}

	/***************************************
	 * Computes the algorithm for all coefficients in the given partition.
	 *
	 * @param  rCoefficients The array of all coefficients
	 * @param  nStart        The partition start index (inclusive)
	 * @param  nEnd          The partition end index (exclusive)
	 *
	 * @return The computation result
	 */
	private static double compute(long[] rCoefficients, int nStart, int nEnd)
	{
		double fResult = 0;

		for (int i = nStart; i < nEnd; i++)
		{
			fResult += Math.sin(Math.pow(rCoefficients[i], 1.1)) + 1e-8;
		}

		return fResult;
	}

	/***************************************
	 * Starts coroutines for the computation of two half-size partitions unless
	 * the batch size is reached in which case {@link #compute(long[], int,
	 * int)} is invoked and added to the result.
	 *
	 * @param rScope        The scope
	 * @param rCoefficients The array of all coefficients to add
	 * @param nStart        The full partition start index (inclusive)
	 * @param nEnd          The full partition end index (exclusive)
	 */
	private static void computeAsync(CoroutineScope rScope,
									 long[]			rCoefficients,
									 int			nStart,
									 int			nEnd)
	{
		if (nEnd - nStart <= BATCH_SIZE)
		{
			rScope.get(RESULT).add(compute(rCoefficients, nStart, nEnd));
		}
		else
		{
			new Coroutine<>(
				run(() ->
						computeAsync(
							rScope,
							rCoefficients,
							nStart,
							nStart + (nEnd - nStart) / 2))).runAsync(rScope);

			new Coroutine<>(
				run(() ->
						computeAsync(
							rScope,
							rCoefficients,
							nStart + (nEnd - nStart) / 2,
							nEnd))).runAsync(rScope);
		}
	}
}
