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
package ca.mcgill.sis.dmas.kam1n0.vex.expression;

import com.fasterxml.jackson.annotation.JsonProperty;

import ca.mcgill.sis.dmas.kam1n0.graph.ComputationGraph;
import ca.mcgill.sis.dmas.kam1n0.graph.ComputationNode;
import ca.mcgill.sis.dmas.kam1n0.vex.VexArchitecture;
import ca.mcgill.sis.dmas.kam1n0.vex.VexExpression;
import ca.mcgill.sis.dmas.kam1n0.vex.VexStatement.VexToStrState;
import ca.mcgill.sis.dmas.kam1n0.vex.enumeration.VexExpressionType;
import ca.mcgill.sis.dmas.kam1n0.vex.enumeration.VexVariableType;

public class ExGet extends VexExpression {

	public int offset;
	public VexVariableType type;

	public ExGet(@JsonProperty("offset") int offset, @JsonProperty("type") VexVariableType type) {
		this.tag = VexExpressionType.Iex_Get;
		this.type = type;
		this.offset = offset;
	}

	@Override
	public ComputationNode getNode(ComputationGraph graph, long ina) {
		ComputationNode node = graph.getReg(offset, type);
		return node;
	}

	@Override
	public void updateTmpOffset(int newOffset) {
	}

	@Override
	public String toStr(VexToStrState state) {
		return state.arch.type.getGuestInfo().registerName.get(offset);
	}
}
