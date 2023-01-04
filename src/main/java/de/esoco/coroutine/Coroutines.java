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

import de.esoco.lib.event.EventDispatcher;
import org.obrel.core.Relatable;
import org.obrel.core.Relation;
import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;
import org.obrel.type.MetaTypes;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.obrel.core.RelationTypes.newDefaultValueType;
import static org.obrel.core.RelationTypes.newInitialValueType;
import static org.obrel.core.RelationTypes.newType;

/**
 * Contains global {@link Coroutine} management functions and relation types. If
 * not stated otherwise the configuration relation types can be set on any level
 * from coroutine to context.
 *
 * @author eso
 */
public class Coroutines {

	/**
	 * Configuration: A handler for coroutine exceptions. The main purpose of
	 * this is to process exception stacktraces when they occur. All coroutine
	 * exceptions will also be available from the finished scope.The default
	 * value prints the stacktrace of a failed coroutine execution to the
	 * console.
	 */
	public static final RelationType<Consumer<Throwable>> EXCEPTION_HANDLER =
		newDefaultValueType((Consumer<Throwable>) (t -> t.printStackTrace()));

	/**
	 * Configuration: coroutine event listeners that will be invoked when
	 * coroutines are started or finished.
	 */
	public static final RelationType<EventDispatcher<CoroutineEvent>>
		COROUTINE_LISTENERS = newInitialValueType(r -> new EventDispatcher<>());

	/**
	 * Configuration: a single listener for coroutine suspensions. This listener
	 * will be invoked with the suspension and a boolean value after a coroutine
	 * has been suspended (TRUE) or before it is resumed (FALSE). This relation
	 * is intended mainly for debugging purposes.
	 */
	public static final RelationType<BiConsumer<Suspension<?>, Boolean>>
		COROUTINE_SUSPENSION_LISTENER = newType();

	/**
	 * Configuration: a single listener for coroutine step executions. This
	 * listener will be invoked with the step and continuation just before a
	 * step is executed. This relation is intended mainly for debugging
	 * purposes.
	 */
	public static final RelationType<BiConsumer<CoroutineStep<?, ?>, Continuation<?>>>
		COROUTINE_STEP_LISTENER = newType();

	private static CoroutineContext defaultContext = new CoroutineContext();

	static {
		RelationTypes.init(Coroutines.class);
	}

	/**
	 * Private, only static use.
	 */
	private Coroutines() {
	}

	/**
	 * Iterates over all relations in the given state object that are annotated
	 * with {@link MetaTypes#MANAGED} and closes them if they implement the
	 * {@link AutoCloseable} interface. This is invoked automatically
	 *
	 * @param state        The state relatable to check for managed resources
	 * @param errorHandler A consumer for exceptions that occur when closing a
	 *                     resource
	 */
	public static void closeManagedResources(Relatable state,
		Consumer<Throwable> errorHandler) {
		state.streamRelations()
			.filter(r -> r.hasAnnotation(
				MetaTypes.MANAGED) && r.getTarget() != null)
			.map(Relation::getTarget)
			.forEach(t -> {
				if (t instanceof AutoCloseable) {
					try {
						((AutoCloseable) t).close();
					} catch (Exception e) {
						errorHandler.accept(e);
					}
				}
			});
	}

	/**
	 * Returns the default {@link CoroutineContext}.
	 *
	 * @return The default context
	 */
	public static CoroutineContext getDefaultContext() {
		return defaultContext;
	}

	/**
	 * Sets the default {@link CoroutineContext}. The context will be used for
	 * all coroutines that are started without an explicit context.
	 *
	 * @param context The new default context or NULL for none
	 */
	public static void setDefaultContext(CoroutineContext context) {
		Objects.requireNonNull(context);

		defaultContext = context;
	}
}
