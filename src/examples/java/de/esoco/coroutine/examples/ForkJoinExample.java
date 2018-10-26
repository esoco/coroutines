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
 * An example of a fork-join coroutine execution like {@link ForkJoinTask}. This
 * implementation is derived from <a
 * href="https://github.com/Kotlin/kotlinx.coroutines/blob/master/benchmarks/src/jmh/kotlin/benchmarks/ForkJoinBenchmark.kt">
 * the Kotlin Fork-Join benchmark</a> and results should be comparable.
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
	 * Main.
	 *
	 * @param rArgs
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
				scope -> computeOrSplit(scope, aCoefficients, 0, TASK_SIZE));

		System.out.printf("Sum: %f\n", aResult.get().sum());

		aProfiler.measure(
			String.format(
				"%d tasks\nwith a batch size of %d",
				TASK_SIZE,
				BATCH_SIZE));
		aProfiler.printSummary();
	}

	/***************************************
	 * computes the result if the partition size is below the batch size
	 * threshold or else splits the partition into two halves and retries
	 * recursively.
	 *
	 * @param rScope        The scope
	 * @param rCoefficients The array of all coefficients to add
	 * @param nStart        The full partition start index (inclusive)
	 * @param nEnd          The full partition end index (exclusive)
	 */
	private static void computeOrSplit(CoroutineScope rScope,
									   long[]		  rCoefficients,
									   int			  nStart,
									   int			  nEnd)
	{
		if (nEnd - nStart <= BATCH_SIZE)
		{
			double fResult = 0;

			for (int i = nStart; i < nEnd; i++)
			{
				fResult += Math.sin(Math.pow(rCoefficients[i], 1.1)) + 1e-8;
			}

			// use a synchronized relation value to collect the result
			rScope.get(RESULT).add(fResult);
		}
		else
		{
			new Coroutine<>(
				run(() ->
						computeOrSplit(
							rScope,
							rCoefficients,
							nStart,
							nStart + (nEnd - nStart) / 2))).runAsync(rScope);

			new Coroutine<>(
				run(() ->
						computeOrSplit(
							rScope,
							rCoefficients,
							nStart + (nEnd - nStart) / 2,
							nEnd))).runAsync(rScope);
		}
	}
}
