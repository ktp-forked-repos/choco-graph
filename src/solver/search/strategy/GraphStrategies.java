/*
 * Copyright (c) 1999-2012, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Created by IntelliJ IDEA.
 * User: Jean-Guillaume Fages
 * Date: 08/08/12
 * Time: 15:27
 */

package solver.search.strategy;

import solver.cstrs.IGraphRelaxation;
import solver.search.GraphAssignment;
import solver.search.GraphDecision;
import solver.search.strategy.decision.Decision;
import solver.variables.IGraphVar;
import util.objects.setDataStructures.ISet;

public class GraphStrategies extends GraphStrategy {


    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    // heuristics
    public static final int LEX = 0;
    public static final int MIN_P_DEGREE = 1;
    public static final int MAX_P_DEGREE = 2;
    public static final int MIN_M_DEGREE = 3;
    public static final int MAX_M_DEGREE = 4;
    public static final int MIN_DELTA_DEGREE = 5;
    public static final int MAX_DELTA_DEGREE = 6;
    public static final int MIN_COST = 7;
    public static final int MAX_COST = 8;
    public static final int IN_SUPPORT_LEX = 9;
    public static final int OUT_SUPPORT_LEX = 10;
    public static final int MIN_REDUCED_COST = 11;
    public static final int MAX_REDUCED_COST = 12;
    public static final int MIN_REPLACEMENT_COST = 13;
    public static final int MAX_REPLACEMENT_COST = 14;

    // variables
    private int n;
    private int mode;
    private int[][] costs;
    private IGraphRelaxation relax;
    private GraphAssignment decisionType;
    private int from, to;
    private int value;
	private boolean useLC;
	private int lastFrom=-1;

    /**
     * Search strategy for graphs
     *
     * @param graphVar   varriable to branch on
     * @param costMatrix can be null
     * @param relaxation can be null
     */
    public GraphStrategies(IGraphVar graphVar, int[][] costMatrix, IGraphRelaxation relaxation) {
        super(graphVar, null, null, NodeArcPriority.ARCS);
        costs = costMatrix;
        relax = relaxation;
        n = g.getNbMaxNodes();
    }

    /**
     * Configure the search
     *
     * @param policy  way to select arcs
     * @param enforce true if a decision is an arc enforcing
     *                false if a decision is an arc removal
     */
    public void configure(int policy, boolean enforce) {
        if (enforce) {
            decisionType = GraphAssignment.graph_enforcer;
        } else {
            decisionType = GraphAssignment.graph_remover;
        }
        mode = policy;
    }

	public void useLastConflict(){
		useLC = true;
	}

    @Override
    public Decision getDecision() {
        if (g.isInstantiated()) {
            return null;
        }
        GraphDecision dec = pool.getE();
        if (dec == null) {
            dec = new GraphDecision(pool);
        }
        computeNextArc();
        dec.setArc(g, from, to, decisionType);
		lastFrom = from;
        return dec;
    }

    private void computeNextArc() {
		to = -1;
		from = -1;
		if(useLC && lastFrom !=-1){
			evaluateNeighbors(lastFrom);
			if(to != -1) {
				return;
			}
		}
        for (int i = 0; i < n; i++) {
            if (evaluateNeighbors(i)) {
                return;
            }
        }
        if (to == -1) {
            throw new UnsupportedOperationException();
        }
    }

    private boolean evaluateNeighbors(int i) {
        ISet set = g.getPotSuccOrNeighOf(i);
        if (set.getSize() == g.getMandSuccOrNeighOf(i).getSize()) {
            return false;
        }
        for (int j = set.getFirstElement(); j >= 0; j = set.getNextElement()) {
            if (!g.getMandSuccOrNeighOf(i).contain(j)) {
                int v = -1;
                switch (mode) {
                    case LEX:
                        from = i;
                        to = j;
                        return true;
                    case MIN_P_DEGREE:
                    case MAX_P_DEGREE:
                        v = g.getPotSuccOrNeighOf(i).getSize()
                                + g.getPotPredOrNeighOf(j).getSize();
                        break;
                    case MIN_M_DEGREE:
                    case MAX_M_DEGREE:
                        v = g.getMandSuccOrNeighOf(i).getSize()
                                + g.getMandPredOrNeighOf(j).getSize();
                        break;
                    case MIN_DELTA_DEGREE:
                    case MAX_DELTA_DEGREE:
                        v = g.getPotSuccOrNeighOf(i).getSize()
                                + g.getPotPredOrNeighOf(j).getSize()
                                - g.getMandSuccOrNeighOf(i).getSize()
                                - g.getMandPredOrNeighOf(j).getSize();
                        break;
                    case MIN_COST:
                    case MAX_COST:
                        v = costs[i][j];
                        break;
                    case IN_SUPPORT_LEX:
                        if (relax.contains(i, j)) {
                            from = i;
                            to = j;
                            return true;
                        }
                        break;
                    case OUT_SUPPORT_LEX:
                        if (!relax.contains(i, j)) {
                            from = i;
                            to = j;
                            return true;
                        }
                        break;
                    case MIN_REDUCED_COST:
                    case MAX_REDUCED_COST:
                        v = (int) relax.getMarginalCost(i, j);
                        break;
                    case MIN_REPLACEMENT_COST:
                    case MAX_REPLACEMENT_COST:
                        v = (int) relax.getReplacementCost(i, j);
                        break;
                    default:
                        throw new UnsupportedOperationException("mode " + mode + " does not exist");
                }
                if (select(v)) {
                    if (mode < MIN_REDUCED_COST
                            || (mode < MIN_REPLACEMENT_COST && !relax.contains(i, j))
                            || relax.contains(i, j)) {
                        value = v;
                        from = i;
                        to = j;
                    }
                }
            }
        }
        return false;
    }

    private boolean select(double v) {
        return (from == -1 || (v < value && isMinOrIn(mode)) || (v > value && !isMinOrIn(mode)));
    }

    private static boolean isMinOrIn(int policy) {
        return (policy % 2 == 1);
    }
}
