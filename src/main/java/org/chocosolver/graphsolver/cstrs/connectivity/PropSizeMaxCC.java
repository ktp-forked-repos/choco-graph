package org.chocosolver.graphsolver.cstrs.connectivity;


import org.chocosolver.graphsolver.util.ConnectivityFinder;
import org.chocosolver.graphsolver.variables.GraphEventType;
import org.chocosolver.graphsolver.variables.UndirectedGraphVar;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.util.ESat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Propagator ensuring that the number of vertices of the largest connected is maxSizeCC
 * (cf. MAX_NCC graph property, http://www.emn.fr/x-info/sdemasse/gccat/sec2.2.2.4.html#uid922).
 */
public class PropSizeMaxCC extends Propagator<Variable> {

    /* Variables */

    private UndirectedGraphVar g;
    private IntVar sizeMaxCC;
    private ConnectivityFinder GLBCCFinder, GUBCCFinder;

    /* Constructor */

    public PropSizeMaxCC(UndirectedGraphVar graph, IntVar sizeMaxCC) {
        super(new Variable[] {graph, sizeMaxCC}, PropagatorPriority.QUADRATIC, false);
        this.g = graph;
        this.sizeMaxCC = sizeMaxCC;
        this.GLBCCFinder = new ConnectivityFinder(g.getLB());
        this.GUBCCFinder = new ConnectivityFinder(g.getUB());
    }

    /* Methods */

    @Override
    public int getPropagationConditions(int vIdx) {
        return GraphEventType.ALL_EVENTS;
    }

    /**
     * @return The lower bound of the graph variable MAX_NCC property.
     * Beware that this.GLBCCFinder.findAllCC() and this.GLBCCFinder.findCCSizes() must be called before.
     */
    private int getMaxNCC_LB() {
        return GLBCCFinder.getSizeMaxCC();
    }

    /**
     * @return The upper bound of the graph variable MAX_NCC property.
     * Beware that this.GUBCCFinder.findAllCC() and this.GUBCCFinder.findCCSizes() must be called before.
     */
    private int getMaxNCC_UB() {
        return GUBCCFinder.getSizeMaxCC();
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        // Find CCs and their sizes
        this.GLBCCFinder.findAllCC();
        this.GUBCCFinder.findAllCC();
        int nbCC_GLB = GLBCCFinder.getNBCC();
        // Retrieve MAX_NCC(g) lower and upper bounds from g
        int maxNCC_LB = getMaxNCC_LB();
        int maxNCC_UB = getMaxNCC_UB();
        // 1. Trivial case
        if (sizeMaxCC.getLB() > g.getUB().getNodes().size()) {
            fails();
        }
        // 2. Trivial case TODO: How to properly get |E_TU| ?
        // 3.
        if (maxNCC_UB < sizeMaxCC.getLB()) {
            fails();
        }
        // 4.
        if (maxNCC_LB > sizeMaxCC.getUB()) {
            fails();
        }
        // 5.
        if (sizeMaxCC.getLB() < maxNCC_LB) {
            sizeMaxCC.updateLowerBound(maxNCC_LB, this);
        }
        // 6.
        if (sizeMaxCC.getUB() > maxNCC_UB) {
            sizeMaxCC.updateUpperBound(maxNCC_UB, this);
        }
        // 7.
        if (maxNCC_UB > sizeMaxCC.getUB()) {
            boolean recomputeMaxNCC_UB = false;
            // a.
            if (sizeMaxCC.getUB() == 1) {
                for (int i = 0; i < g.getPotentialNodes().size(); i++) {
                    for (int j = i + 1; j < g.getPotentialNodes().size(); j++) {
                        g.removeArc(i, j, this);
                    }
                }
            }
            // b.
            if (sizeMaxCC.getUB() == 0) {
                for (int i : g.getPotentialNodes()) {
                    g.removeNode(i, this);
                }
            }
            for (int cc = 0; cc < nbCC_GLB; cc++) {
                int[] sizeCC = GLBCCFinder.getSizeCC();
                // c.
                if (sizeCC[cc] == sizeMaxCC.getUB()) {
                    Map<Integer, Set<Integer>> ccPotentialNeighbors = getGLBCCPotentialNeighbors(cc);
                    for (int i : ccPotentialNeighbors.keySet()) {
                        for (int j : ccPotentialNeighbors.get(i)) {
                            g.removeArc(i, j, this);
                        }
                    }
                } else {
                    // d.
                    for (int cc2 = cc + 1; cc2 < nbCC_GLB; cc2++) {
                        if (sizeCC[cc] + sizeCC[cc2] > sizeMaxCC.getUB()) {
                            Map<Integer, Set<Integer>> ccPotentialNeighbors = getGLBCCPotentialNeighbors(cc);
                            for (int i : ccPotentialNeighbors.keySet()) {
                                for (int j : ccPotentialNeighbors.get(i)) {
                                    if (getGLBCCNodes(cc2).contains(j)) {
                                        recomputeMaxNCC_UB = true;
                                        g.removeArc(i, j, this);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // e.
            if (recomputeMaxNCC_UB) {
                this.GUBCCFinder.findAllCC();
                maxNCC_UB = getMaxNCC_UB();
                if (maxNCC_UB < sizeMaxCC.getLB()) {
                    fails();
                }
                if (sizeMaxCC.getUB() > maxNCC_UB) {
                    sizeMaxCC.updateUpperBound(maxNCC_UB, this);
                }
            }
        }
        // 8.
        int nb_candidates = 0;
        int candidate = -1;
        int size = 0;
        for (int cc = 0; cc < GUBCCFinder.getNBCC(); cc++) {
            int s = GUBCCFinder.getSizeCC()[cc];
            if (s >= sizeMaxCC.getLB()) {
                nb_candidates ++;
                candidate = cc;
                size = s;
            }
            if (nb_candidates > 1) {
                break;
            }
        }
        if (nb_candidates == 1 && size == sizeMaxCC.getLB()) {
            int i = GUBCCFinder.getCC_firstNode()[candidate];
            while (i != -1) {
                g.enforceNode(i, this);
                i = GUBCCFinder.getCC_nextNode()[i];
            }
            sizeMaxCC.instantiateTo(sizeMaxCC.getLB(), this);
        }
    }

    /**
     * Retrieve the nodes of a GLB CC.
     * @param cc The GLB CC index.
     * @return The set of nodes of the GLB CC cc.
     */
    private Set<Integer> getGLBCCNodes(int cc) {
        Set<Integer> ccNodes = new HashSet<>();
        for (int i = GLBCCFinder.getCC_firstNode()[cc]; i >= 0; i = GLBCCFinder.getCC_nextNode()[i]) {
            ccNodes.add(i);
        }
        return ccNodes;
    }

    /**
     * Retrieve the potential CC neighbors (i.e. not in the CC) of a GLB CC.
     * @param cc The GLB CC index.
     * @return A map with frontier nodes of the CC as keys (Integer), and their potential neighbors that are
     * outside the CC (Set<Integer>). Only the frontier nodes that have at least one potential neighbor outside the
     * CC are stored in the map.
     * {
     *     frontierNode1: {out-CC potential neighbors},
     *     frontierNode3: {...},
     *     ...
     * }
     */
    private Map<Integer, Set<Integer>> getGLBCCPotentialNeighbors(int cc) {
        Map<Integer, Set<Integer>> ccPotentialNeighbors = new HashMap<>();
        // Retrieve all nodes of CC
        Set<Integer> ccNodes = getGLBCCNodes(cc);
        // Retrieve neighbors of the nodes of CC that are outside the CC
        for (int i : ccNodes) {
            Set<Integer> outNeighbors = new HashSet<>();
            for (int j : g.getPotNeighOf(i)) {
                if (!ccNodes.contains(j)) {
                    outNeighbors.add(j);
                }
            }
            if (outNeighbors.size() > 0) {
                ccPotentialNeighbors.put(i, outNeighbors);
            }
        }
        return ccPotentialNeighbors;
    }


    @Override
    public ESat isEntailed() {
        // Find CCs and their sizes
        this.GLBCCFinder.findAllCC();
        this.GUBCCFinder.findAllCC();
        // Retrieve MAX_NCC(g) lower and upper bounds from g
        int maxNCC_LB = getMaxNCC_LB();
        int maxNCC_UB = getMaxNCC_UB();
        // Check entailment
        if (maxNCC_UB < sizeMaxCC.getLB() || maxNCC_LB > sizeMaxCC.getUB()) {
            return ESat.FALSE;
        }
        if (isCompletelyInstantiated()) {
            if (maxNCC_LB == sizeMaxCC.getValue()) {
                return ESat.TRUE;
            } else {
                return ESat.FALSE;
            }
        }
        return ESat.UNDEFINED;
    }
}
