//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-lib' project.
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


/********************************************************************
 * This is a marker interface for identifiers of {@link Channel channels} which
 * are used for communication between {@link Coroutine coroutines}. One possible
 * usage would be to implement it on enums or similar constants that are to be
 * used as channel IDs. IDs are generically typed with the datatype of the
 * target channel. Implementations are responsible to that their channel IDs are
 * unique also with respect to the datatype.
 *
 * <p>Instances of a default implementation of string-based IDs can be created
 * through the generic factory method {@link #channel(String, Class)}. Please
 * check the method comment for informations about the constraints imposed by
 * that implementation. There are also some derived factory methods for common
 * datatypes like {@link #stringChannel(String)}.</p>
 *
 * @author eso
 */
public interface ChannelId<T>
{
	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Creates a channel ID with a boolean datatype.
	 *
	 * @param  sId The ID string
	 *
	 * @return The new ID
	 *
	 * @see    #channel(String, Class)
	 */
	public static ChannelId<Boolean> booleanChannel(String sId)
	{
		return channel(sId, Boolean.class);
	}

	/***************************************
	 * Creates a new channel ID from an identifier string. Channel IDs with the
	 * same string are considered equal and will therefore give access to the
	 * same channel in a {@link CoroutineContext}. String IDs won't be cached so
	 * that invocations with the the same string will return different instances
	 * although these are considered equal. To avoid name clashes in complex
	 * scenarios the ID names should be selected appropriately, e.g. by using
	 * namespaces. The implementation doesn't impose any restrictions on the
	 * strings used to define IDs.
	 *
	 * <p>Equality of string-based IDs is also based on the datatype. That means
	 * that it is possible to create equal-named channel IDs for different
	 * datatypes. Accessing channels through such IDs would yield different
	 * channel instances. It lies in the responsibility of the application to
	 * name channel IDs appropriately for the respective context.</p>
	 *
	 * @param  sId       The channel ID string
	 * @param  rDatatype The class of the channel datatype to ensure
	 *
	 * @return A new {@link StringId}
	 */
	public static <T> ChannelId<T> channel(String sId, Class<T> rDatatype)
	{
		Objects.requireNonNull(sId);
		Objects.requireNonNull(rDatatype);

		return new StringId<>(sId, rDatatype);
	}

	/***************************************
	 * Creates a channel ID with an integer datatype.
	 *
	 * @param  sId The ID string
	 *
	 * @return The new ID
	 *
	 * @see    #channel(String, Class)
	 */
	public static ChannelId<Integer> intChannel(String sId)
	{
		return channel(sId, Integer.class);
	}

	/***************************************
	 * Creates a channel ID with a string datatype.
	 *
	 * @param  sId The ID string
	 *
	 * @return The new ID
	 *
	 * @see    #channel(String, Class)
	 */
	public static ChannelId<String> stringChannel(String sId)
	{
		return channel(sId, String.class);
	}

	//~ Inner Classes ----------------------------------------------------------

	/********************************************************************
	 * Internal implementation of string-based channel IDs.
	 *
	 * @author eso
	 */
	static class StringId<T> implements ChannelId<T>
	{
		//~ Instance fields ----------------------------------------------------

		private final String   sId;
		private final Class<T> rDatatype;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance.
		 *
		 * @param sId       The ID string
		 * @param rDatatype The channel datatype
		 */
		StringId(String sId, Class<T> rDatatype)
		{
			this.sId	   = sId;
			this.rDatatype = rDatatype;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object rObject)
		{
			if (this == rObject)
			{
				return true;
			}

			if (rObject == null || getClass() != rObject.getClass())
			{
				return false;
			}

			StringId<?> rOther = (StringId<?>) rObject;

			return rDatatype == rOther.rDatatype && sId.equals(rOther.sId);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode()
		{
			return 17 * rDatatype.hashCode() + sId.hashCode();
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public String toString()
		{
			return ChannelId.class.getSimpleName() +
				   String.format("[%s-%s]", sId, rDatatype.getSimpleName());
		}
	}
}
