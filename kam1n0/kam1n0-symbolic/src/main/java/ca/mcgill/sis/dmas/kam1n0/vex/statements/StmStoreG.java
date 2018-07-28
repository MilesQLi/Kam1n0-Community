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
package ca.mcgill.sis.dmas.kam1n0.vex.statements;

import com.fasterxml.jackson.annotation.JsonProperty;

import ca.mcgill.sis.dmas.kam1n0.graph.ComputationGraph;
import ca.mcgill.sis.dmas.kam1n0.graph.ComputationNode;
import ca.mcgill.sis.dmas.kam1n0.vex.VexArchitecture;
import ca.mcgill.sis.dmas.kam1n0.vex.VexExpression;
import ca.mcgill.sis.dmas.kam1n0.vex.VexStatement;
import ca.mcgill.sis.dmas.kam1n0.vex.VexStatement.VexToStrState;
import ca.mcgill.sis.dmas.kam1n0.vex.enumeration.VexEndnessType;
import ca.mcgill.sis.dmas.kam1n0.vex.enumeration.VexStatementType;

public class StmStoreG extends VexStatement {
	public VexEndnessType endness;
	public VexExpression addr;
	public VexExpression data;
	public VexExpression guard;

	public StmStoreG(@JsonProperty("endness") VexEndnessType endness, @JsonProperty("addr") VexExpression addr,
			@JsonProperty("data") VexExpression data, @JsonProperty("guard") VexExpression guard) {
		super();
		this.endness = endness;
		this.addr = addr;
		this.data = data;
		this.guard = guard;
		this.tag = VexStatementType.Ist_StoreG;
	}

	@Override
	public ComputationNode translate(ComputationGraph graph) {
		ComputationNode addr = this.addr.getNode(graph, this.ina);
		ComputationNode data = this.data.getNode(graph, this.ina);
		ComputationNode memVar = graph.memory.readMem(this.ina, addr, data.valType.outputType, endness, graph);

		ComputationNode node;
		if (this.guard != null) {
			ComputationNode guard = this.guard.getNode(graph, this.ina);
			ComputationNode ifc = graph.createCondition(guard, data, memVar);
			node = graph.memory.writeMem(addr, ifc, endness, graph);
			return node;
		} else {
			node = graph.memory.writeMem(addr, data, endness, graph);
			return node;
		}
	}

	@Override
	public void updateTmpOffset(int newOffset) {
		addr.updateTmpOffset(newOffset);
		data.updateTmpOffset(newOffset);
		guard.updateTmpOffset(newOffset);
	}

	@Override
	public String toStr(VexToStrState state) {
		return "if(" + guard.toStr(state) + "==1)then{[" + addr.toStr(state) + "]=" + data.toStr(state) + "}";
	}
}
