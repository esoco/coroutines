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

import de.esoco.coroutine.step.Collect;
import de.esoco.coroutine.step.Select;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static de.esoco.coroutine.ChannelId.stringChannel;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.ChannelReceive.receive;
import static de.esoco.coroutine.step.Collect.collect;
import static de.esoco.coroutine.step.Select.select;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/********************************************************************
 * Test of {@link Selection} steps {@link Select} and {@link Collect}.
 *
 * @author eso
 */
public class SelectionTest
{
	//~ Static fields/initializers ---------------------------------------------

	private static final ChannelId<String> CHANNEL_A = stringChannel("A");
	private static final ChannelId<String> CHANNEL_B = stringChannel("B");
	private static final ChannelId<String> CHANNEL_C = stringChannel("C");

	private static final List<ChannelId<String>> ALL_CHANNELS =
		Arrays.asList(CHANNEL_A, CHANNEL_B, CHANNEL_C);

	private static final Coroutine<Void, String> SELECT_ABC =
		Coroutine.first(
			select(receive(CHANNEL_A)).or(receive(CHANNEL_B))
			.or(receive(CHANNEL_C)));

	private static final Coroutine<Void, Collection<String>> COLLECT_ABC =
		Coroutine.first(
			collect(receive(CHANNEL_A)).and(receive(CHANNEL_B))
			.and(receive(CHANNEL_C)));

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Test class setup.
	 */
	@BeforeClass
	public static void setup()
	{
		// suppress stacktraces from error testing
//		Coroutines.getDefaultContext().set(EXCEPTION_HANDLER, t ->{});
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Test of channel select.
	 */
	@Test
	public void testChannelCollect()
	{
		testCollect(true);
		testCollect(false);
	}

	/***************************************
	 * Test of channel select.
	 */
	@Test
	public void testChannelSelect()
	{
		// execute multiple times to test for "cross-talk" between calls
		testSelect(CHANNEL_A, true);
		testSelect(CHANNEL_B, true);
		testSelect(CHANNEL_C, true);
		testSelect(CHANNEL_A, false);
		testSelect(CHANNEL_B, false);
		testSelect(CHANNEL_C, false);
	}

	/***************************************
	 * Test of collecting from channels.
	 *
	 * @param bAsync Async or blocking
	 */
	private void testCollect(boolean bAsync)
	{
		launch(
			scope ->
			{
				if (!bAsync)
				{
					// send first if blocking or else scope will remain blocked
					ALL_CHANNELS.forEach(
						id -> scope.getChannel(id).sendBlocking(id.toString()));
				}

				Continuation<Collection<String>> c =
					bAsync ? COLLECT_ABC.runAsync(scope)
						   : COLLECT_ABC.runBlocking(scope);

				if (bAsync)
				{
					ALL_CHANNELS.forEach(
						id -> scope.getChannel(id).sendBlocking(id.toString()));
				}

				Collection<String> rResult = c.getResult();

				ALL_CHANNELS.forEach(
					id -> assertTrue(rResult.contains(id.toString())));
				assertTrue(c.isFinished());
			});
	}

	/***************************************
	 * Test of selecting from a certain channel.
	 *
	 * @param rId    The channel ID
	 * @param bAsync Async or blocking
	 */
	private void testSelect(ChannelId<String> rId, boolean bAsync)
	{
		launch(
			scope ->
			{
				Channel<String> channel = scope.getChannel(rId);

				if (!bAsync)
				{
					// send first if blocking or else scope will remain blocked
					channel.sendBlocking(rId.toString());
				}

				Continuation<String> c =
					bAsync ? SELECT_ABC.runAsync(scope)
						   : SELECT_ABC.runBlocking(scope);

				if (bAsync)
				{
					channel.sendBlocking(rId.toString());
				}

				assertEquals(rId.toString(), c.getResult());
				assertTrue(c.isFinished());
			});
	}
}
