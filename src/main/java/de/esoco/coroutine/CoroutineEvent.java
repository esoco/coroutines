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

import de.esoco.lib.event.GenericEvent;

/********************************************************************
 * The event object for coroutine events.
 *
 * @author eso
 */
public class CoroutineEvent extends GenericEvent<Continuation<?>> {
    //~ Enums ------------------------------------------------------------------

    /********************************************************************
     * The available event types.
     */
    public enum EventType {
        STARTED, FINISHED
    }

    //~ Instance fields --------------------------------------------------------

    private final EventType eType;

    //~ Constructors -----------------------------------------------------------

    /***************************************
     * Creates a new instance.
     *
     * @param rContinuation The continuation of the coroutine execution
     * @param eType         The event type
     */
    public CoroutineEvent(Continuation<?> rContinuation, EventType eType) {
        super(rContinuation);

        this.eType = eType;
    }

    //~ Methods ----------------------------------------------------------------

    /***************************************
     * Returns the event type.
     *
     * @return The event type
     */
    public EventType getType() {
        return eType;
    }

    /***************************************
     * {@inheritDoc}
     */
    @Override
    protected String paramString() {
        return eType.name();
    }
}
