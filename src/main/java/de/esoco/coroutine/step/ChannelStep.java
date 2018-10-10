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
package de.esoco.coroutine.step;

import de.esoco.coroutine.Channel;
import de.esoco.coroutine.ChannelId;
import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.CoroutineException;
import de.esoco.coroutine.CoroutineStep;

import java.util.Objects;

import org.obrel.core.RelationType;


/********************************************************************
 * A base class for coroutine steps that perform {@link Channel} operations.
 *
 * @author eso
 */
public abstract class ChannelStep<I, O> extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private ChannelId<O>			   rChannelId;
	private RelationType<ChannelId<O>> rChannelType;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that operates on a certain channel.
	 *
	 * @param rId The ID of the channel to operate on
	 */
	public ChannelStep(ChannelId<O> rId)
	{
		Objects.requireNonNull(rId);

		this.rChannelId = rId;
	}

	/***************************************
	 * Creates a new instance that operates on a channel the ID of which is
	 * provided in a certain state relation.
	 *
	 * @param rChannelType The type of the state relation the channel ID is
	 *                     stored in
	 */
	public ChannelStep(RelationType<ChannelId<O>> rChannelType)
	{
		Objects.requireNonNull(rChannelType);

		this.rChannelType = rChannelType;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Returns the channel this step operates on.
	 *
	 * @param  rContinuation The current continuation
	 *
	 * @return The channel
	 */
	public Channel<O> getChannel(Continuation<?> rContinuation)
	{
		ChannelId<O> rId = rChannelId;

		if (rId == null)
		{
			rId = rContinuation.getState(rChannelType);

			if (rId == null)
			{
				throw new CoroutineException(
					"No channel ID in %s",
					rChannelType);
			}
		}

		return rContinuation.getChannel(rId);
	}
}
