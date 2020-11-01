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

import de.esoco.lib.collection.CollectionUtil;

import java.util.Collection;

/********************************************************************
 * A runtime exception that is thrown if one or more {@link Coroutine}
 * executions in a {@link CoroutineScope} fail with an exception.
 *
 * @author eso
 */
public class CoroutineScopeException extends CoroutineException {
    //~ Static fields/initializers ---------------------------------------------

    private static final long serialVersionUID = 1L;

    //~ Instance fields --------------------------------------------------------

    private final Collection<Continuation<?>> rFailedContinuations;

    //~ Constructors -----------------------------------------------------------

    /***************************************
     * Creates a new instance from the continuations of failed coroutines. The
     * causing exception will be set to the error of the first element in the
     * argument collection.
     *
     * @param rFailed The failed continuations
     */
    public CoroutineScopeException(Collection<Continuation<?>> rFailed) {
        super(CollectionUtil.firstElementOf(rFailed).getError());

        rFailedContinuations = rFailed;
    }

    /***************************************
     * Creates a new instance from an explicit causing exception and optional
     * continuations of failed coroutines.
     *
     * @param eCause  The causing exception
     * @param rFailed The failed continuations (optional)
     */
    public CoroutineScopeException(Throwable eCause,
        Collection<Continuation<?>> rFailed) {
        super(eCause);

        rFailedContinuations = rFailed;
    }

    //~ Methods ----------------------------------------------------------------

    /***************************************
     * Returns the failed continuations that caused this exception. The actual
     * error exceptions can be queried with {@link Continuation#getError()}.
     *
     * @return The continuation
     */
    public Collection<Continuation<?>> getFailedContinuations() {
        return rFailedContinuations;
    }
}
