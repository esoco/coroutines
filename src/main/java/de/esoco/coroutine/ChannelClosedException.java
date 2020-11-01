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

/********************************************************************
 * A coroutine exception that is thrown if a closed channel is accessed.
 *
 * @author eso
 */
public class ChannelClosedException extends CoroutineException {
    //~ Static fields/initializers ---------------------------------------------

    private static final long serialVersionUID = 1L;

    //~ Instance fields --------------------------------------------------------

    private final ChannelId<?> rChannelId;

    //~ Constructors -----------------------------------------------------------

    /***************************************
     * Creates a new instance.
     *
     * @param rId The channel ID
     */
    public ChannelClosedException(ChannelId<?> rId) {
        super("Channel %s is closed", rId);

        rChannelId = rId;
    }

    //~ Methods ----------------------------------------------------------------

    /***************************************
     * Returns the channel id.
     *
     * @return The channel id
     */
    public ChannelId<?> getChannelId() {
        return rChannelId;
    }
}
