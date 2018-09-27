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

import java.util.Objects;
import java.util.function.Consumer;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;

import static org.obrel.core.RelationTypes.newDefaultValueType;


/********************************************************************
 * Contains global {@link Coroutine} management functions.
 *
 * @author eso
 */
public class Coroutines
{
	//~ Static fields/initializers ---------------------------------------------

	private static CoroutineContext rDefaultContext = new CoroutineContext();

	/**
	 * Configuration: Stacktrace handler for coroutine errors. The default value
	 * prints the stacktrace of a failed coroutine execution to the console.
	 */
	public static final RelationType<Consumer<Throwable>> STACKTRACE_HANDLER =
		newDefaultValueType((Consumer<Throwable>) (t -> t.printStackTrace()));

	static
	{
		RelationTypes.init(Coroutines.class);
	}

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Private, only static use.
	 */
	private Coroutines()
	{
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Returns the default {@link CoroutineContext}.
	 *
	 * @return The default context
	 */
	public static CoroutineContext getDefaultContext()
	{
		return rDefaultContext;
	}

	/***************************************
	 * Sets the default {@link CoroutineContext}. The context will be used for
	 * all coroutines that are started without an explicit context.
	 *
	 * @param rContext The new default context or NULL for none
	 */
	public static void setDefaultContext(CoroutineContext rContext)
	{
		Objects.requireNonNull(rContext);

		rDefaultContext = rContext;
	}
}
