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
package de.esoco.coroutine;

import org.junit.jupiter.api.Test;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CallSubroutine.call;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Condition.doIf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test of {@link Subroutine}.
 *
 * @author eso
 */
public class SubroutineTest {

	/**
	 * Test of subroutine invocations.
	 */
	@Test
	public void testSubroutine() {
		Coroutine<String, Integer> cr =
			Coroutine.first(call(CoroutineTest.CONVERT_INT))
				.then(apply(i -> i + 10));

		launch(scope -> {
			Continuation<Integer> ca = cr.runAsync(scope, "test1234");
			Continuation<Integer> cb = cr.runBlocking(scope, "test1234");

			assertEquals(Integer.valueOf(12355), ca.getResult());
			assertEquals(Integer.valueOf(12355), cb.getResult());
			assertTrue(ca.isFinished());
			assertTrue(cb.isFinished());
		});
	}

	/**
	 * Test of early subroutine termination.
	 */
	@Test
	public void testSubroutineTermination() {
		Coroutine<Boolean, String> cr = first(call(first(
			doIf((Boolean b) -> b == Boolean.TRUE, supply(() -> "TRUE")))));

		launch(scope -> {
			Continuation<String> ca = cr.runAsync(scope, false);
			Continuation<String> cb = cr.runBlocking(scope, false);

			assertEquals(null, ca.getResult());
			assertEquals(null, cb.getResult());
			assertTrue(ca.isFinished());
			assertTrue(cb.isFinished());

			ca = cr.runAsync(scope, true);
			cb = cr.runBlocking(scope, true);

			assertEquals("TRUE", ca.getResult());
			assertEquals("TRUE", cb.getResult());
			assertTrue(ca.isFinished());
			assertTrue(cb.isFinished());
		});
	}
}
