/*******************************************************************************
 * Copyright 2017 McGill University All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ca.mcgill.sis.dmas.kam1n0.framework.storage.index;

import java.io.Serializable;
import java.util.HashSet;

import ca.mcgill.sis.dmas.env.StringResources;

public class Bucket implements Serializable {
	private static final long serialVersionUID = -673967819919568909L;

	public Bucket() {
	}
	
	public Bucket(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public Bucket(String key, HashSet<Long> value) {
		super();
		this.key = key;
		this.value = value;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public HashSet<Long> getValue() {
		return value;
	}

	public void setValue(HashSet<Long> value) {
		this.value = value;
	}

	public String key = StringResources.STR_EMPTY;
	public HashSet<Long> value = new HashSet<>();
	
	@Override
	public String toString() {
		return key + " - " + value.toString();
	}
}
