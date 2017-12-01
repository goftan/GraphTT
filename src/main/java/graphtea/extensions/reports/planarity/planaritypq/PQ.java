package graphtea.extensions.reports.planarity.planaritypq;

import org.glassfish.grizzly.utils.ArraySet;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

import static graphtea.extensions.reports.planarity.planaritypq.PQHelpers.*;

/**
 * This class contains subroutines used to construct a PQ-Tree as described
 * in the 1976 paper "Testing for Consecutive Ones Property, Interval Graphs,
 * and Graph Planarity Using PQ-Tree Algorithms" by Kellogg S. Booth and George S. Lueker.
 *
 * We highly recommend reading over the paper before reading this codebase.
 *
 * @author Alex Cregten
 * @author Hannes Kr. Hannesson
 */

public class PQ {

    /**
     * This subroutine determines which nodes of the PQ-Tree should be pruned
     * with respect to the constraint sequence S.
     *
     * @param _root root of the tree/subtree
     * @param S a set of nodes, describes a constraint sequence
     * @return a tree rooted at _root which do not violate S (nor the reverse of S) nor previously applied constraint sequences.
     */
    public PQNode bubble(PQNode _root, List<PQNode> S){
        Queue<PQNode> queue = new LinkedList<>(S);
        int blockCount = 0;
        int blockedNodes = 0;
        int offTheTop = 0;

        while(queue.size() + blockCount + offTheTop > 1){
            //System.out.println("|Queue| = " + queue.size());
            if(queue.size() == 0){
                _root = null;
                return _root;
            }

            PQNode x = queue.remove();
            x.blocked = true;

            //System.out.println("Processing: " + x.id);

            List<PQNode> BS = new ArrayList<>();
            List<PQNode> US = new ArrayList<>();

            // Only QNodes have immediate siblings
            for(PQNode u : x.immediateSiblings(true)){
                if(u == null){
                    continue;
                }
                if(u.blocked){
                    BS.add(u);
                }
                else {
                    US.add(u);
                }
            }
            //System.out.println("|BS| = " + BS.size());
            //System.out.println("|US| = " + US.size());
            if(US.size() > 0){
                PQNode y = US.get(0);
                //x.parent = y.parent;
                x.parent = y.getParent(); // todo: is this still correct??
                x.blocked = false;
            }
            else if(x.immediateSiblings(true).size() < 2){
                x.blocked = false;
            }

            int listSize = 0;
            if(!x.blocked){
                PQNode y = x.parent;
                if(BS.size() > 0){

                    Set<PQNode> list = x.maximalConsecutiveSetOfSiblingsAdjacent(true);
                    if(list != null){
                        listSize = list.size();
                        for(PQNode z : list){
                            z.blocked = false;
                            y.pertinentChildCount++;
                        }
                    }

                }
                if(y == null){
                    offTheTop = 1;
                }
                else {
                    y.pertinentChildCount++;
                    if(!queue.contains(y)){
                        queue.add(y);
                    }
                }
                blockCount = blockCount - BS.size();
                blockedNodes = blockedNodes - listSize;
            }
            else {
                blockCount = blockCount + 1 - BS.size();
                blockedNodes = blockedNodes + 1;
            }

        }

        if(queue.size() > 1){
            System.out.println("Oh no!");
        }

        return _root;
    }

    /**
     * This subroutine does the actual reduction on the tree, such that the resulting tree adheres to the constraint S.
     * This is done by matching the tree to "templates", which are essentially types of trees.
     * After matching the tree to a template, we can replace it with the template's replacement.
     * @param T root of the tree/subtree to reduce
     * @param S a set of nodes, describes a constraint sequence
     * @return a tree rooted at _root which do not violate S (nor the reverse of S) nor previously applied constraint sequences, if a matching
     * does not exist, returns the null tree.
     */
    public PQNode reduce(PQNode T, List<PQNode> S){
        //PQHelpers.printChildren(T);
        Queue<PQNode> queue = new LinkedList<>(S);
        for(PQNode x : S){
            x.pertinentLeafCount = 1;
        }
        while(queue.size() > 0){
            PQNode x = queue.remove();
            //System.out.println("pertinentLeafCount of x: " + x.pertinentLeafCount);
            if(x.pertinentLeafCount < S.size()){
                // X is not ROOT(T, S)

                //PQNode y = x.parent;
                //System.out.println("Finding parent for... " + x.id);
                PQNode y = x.getParent();
                y.pertinentLeafCount = y.pertinentLeafCount + x.pertinentLeafCount;
                y.pertinentChildCount = y.pertinentChildCount - 1;
                if(y.pertinentChildCount == 0){
                    queue.add(y);
                }

                if(TEMPLATE_L1(x)) continue;
                if(TEMPLATE_P1(x)) continue;
                if(TEMPLATE_P3(x)) continue;
                if(TEMPLATE_P5(x)) continue;
                if(TEMPLATE_Q1(x)) continue;
                if(TEMPLATE_Q2(x)) continue;

                // If all templates fail, return null tree
                return null;
            }
            else {
                // X is ROOT(T, S)

                if(TEMPLATE_L1(x)) continue;
                if(TEMPLATE_P1(x)) continue;
                if(TEMPLATE_P2(x)) continue;
                if(TEMPLATE_P4(x)) continue;
                if(TEMPLATE_P6(x)) continue;
                if(TEMPLATE_Q1(x)) continue;
                if(TEMPLATE_Q2(x)) continue;
                if(TEMPLATE_Q3(x)) continue;

                // If all templates fail, return null tree
                return null;
            }

        }

        return T;
    }

    public void replace(PQNode T, PQNode TPrime){

    }

    /**
     * For each element s in S, s_i's ancestors are traversed up to a maximum of T.
     * If, for some j, the element ANCESTOR(s_i) is in the set ANCESTORS(s_j), s_i's traversal stops because that ancestor has already been found.
     * For each s_i, s_j that has a common ancestor, that ancestor is added to a list.  That list then calculates
     * the lowest depth ancestor which will be returned.
     *
     * @param T describes the root of the entire tree
     * @param S is the list of nodes that must be legally reachable
     * @return the root of the subtree that can reach all of S */
    public PQNode root(PQNode T, List<PQNode> S){
        if(S.size() == 0){
            return null;
        }

        if(S.size() == 1){
            return S.get(0);
        }

        List<PQNode> nodesReached = new ArrayList<>();
        List<PQNode> nodesSame = new ArrayList<>();

        for(PQNode s : S){
            PQNode traversal = s;
            while(traversal != T){
                traversal = traversal.getParent();
                if(nodesReached.contains(traversal)){
                    nodesSame.add(traversal);
                    break;
                }
                nodesReached.add(traversal);
            }
        }

        PQNode lowestDepthNode = null;
        int lowestDepth = Integer.MAX_VALUE;
        for(PQNode n : nodesSame){
            PQNode traversal = n;
            int curNodesLowestDepth = 0;
            while(traversal != T){
                traversal = traversal.getParent();
                curNodesLowestDepth++;
            }
            if(lowestDepth > curNodesLowestDepth){
                lowestDepthNode = n;
                lowestDepth = curNodesLowestDepth;
            }
        }

        if(lowestDepthNode == null){
            return nodesSame.get(0);
        }
        else {
            return lowestDepthNode;
        }

    }

    /**
    * TEMPLATES
    * */

    public boolean TEMPLATE_L1(PQNode x){
        if(x.nodeType.equals(PQNode.PNODE) || x.nodeType.equals(PQNode.QNODE)){
            return false;
        }
        System.out.println("TEMPLATE L1");
        return true;
    }

    /**All children must be labelled identically (page 348)*/
    public boolean QNODE_TEMPLATE_1(PQNode x){
        PQNode front = x.endmostChildren().get(0);

        final String consistentLabel = front.labelType;
        PQNode iter = front;

        do {
            if(iter.labelType.equals(consistentLabel)){
                iter = iter.circularLink_next;
            }
            else {
                return false;
            }

        } while (iter != front);

        System.out.println("TEMPLATE Q 1");

        return true;
    }

    //todo: All children must be labelled identically (page 348), not necessarily false?
    public boolean GENERALIZED_TEMPLATE_1(PQNode x){
        if(!x.labelType.equals(PQNode.FULL)){
            for(PQNode n : x.children){
                if(!n.labelType.equals(PQNode.FULL)){
                    return false;
                }
            }
            x.labelType = PQNode.FULL;

            System.out.println("TEMPLATE P (or Q) 1");
            return true;
        }
        return false;
    }

    /**
     * Tries to match the tree/subtree at x to template P1, if successful,
     * we apply it.
     *
     * Case #1:            Case #2:
     * TEMPLATE:
     *    ----(E)---      |    ----(E)---
     *    |        |      |    |        |
     *                    |
     *    E  ....  E      |    F  ....  F
     *                    |
     * REPLACEMENT:       |
     *    ----(E)---      |    ----(F)---
     *    |        |      |    |        |
     *                    |
     *    E  ....  E      |    F  ....  F
     *                    |
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_P1(PQNode x){
       if (x.nodeType.equals(PQNode.PNODE)) {
           return GENERALIZED_TEMPLATE_1(x);
       }
       return false;
    }

    /**
     * Tries to match the tree at x (x must be the root of the pertinent subtree) to template P2, if successful,
     * we apply it.
     *
     * TEMPLATE:
     *
     *    -------------(P)------------
     *    |        |        |        |
     *
     *    E  ....  E        F  ....  F
     *
     * REPLACEMENT:
     *    -------------(P)--------
     *    |        |             |
     *
     *    E  ....  E            (F)
     *                      |        |
     *
     *                      F  ....  F
     *
     * @param x the node which represents the root of the subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_P2(PQNode x) {

        //Matching Phase

        List<PQNode> emptyChildren = new ArrayList<PQNode>();
        List<PQNode> fullChildren = new ArrayList<PQNode>();

        PQHelpers.collectChildrenByLabel(x, emptyChildren, fullChildren);

        if(x.nodeType.equals(PQNode.QNODE)){
            return false;
        }

        //If there are no full nodes
        if (fullChildren.size() == 0) {
            return false;
        }
        //If there are no empty nodes
        if (emptyChildren.size() == 0) {
            return false;
        }
        //If there were other nodes than full or empty
        if ( fullChildren.size() + emptyChildren.size() != x.children.size()) {
            return false;
        }

        // One full child
        if(fullChildren.size() == 1){
            //x.children.add(fullChildren.get(0));
            //fullChildren.get(0).parent = x;
            //setCircularLinks(fullChildren);

            System.out.println("P2: One full child - 'skipped' with true");
            return true;
        }

        //Replacement phase
        PQNode fullParent = new PQNode();
        fullParent.nodeType = PQNode.PNODE;
        fullParent.labelType = PQNode.FULL;
        fullParent.parent = x;

        //Add new pNode to root children list
        x.children.add(fullParent);

        //Adding the full children to a new P node
        fullParent.children = fullChildren;
        x.children.removeAll(fullChildren);


        //Pointing the children to the new P node
        for (PQNode child : fullChildren) {
            child.parent = fullParent;
        }

        //Setting the links again, otherwise the endmost children would point to the previous siblings (the empty ones)
        setCircularLinks(fullChildren);

        System.out.println("TEMPLATE P2");

        return true;
    }

    /**
     * Tries to match the subtree at x (x must NOT be the root of the pertinent subtree) to template P3, if successful,
     * we apply it.
     *
     * Note: This case is very similiar to TEMPLATE_P2.
     *       The matching is nearly identical, the only different being that x cannot be a root.
     *
     * TEMPLATE:
     *
     *    -------------(P)------------
     *    |        |        |        |
     *
     *    E  ....  E        F  ....  F
     *
     * REPLACEMENT:
     *       ---------[P]---------
     *       |                   |
     *
     *  ----(E)---          ----(F)---
     *  |        |          |        |
     *
     *  F  ....  F          F  ....  F
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_P3(PQNode x){

        //Matching Phase
        if(!x.nodeType.equals(PQNode.PNODE)){
            return false;
        }

        List<PQNode> emptyChildren = new ArrayList<PQNode>();
        List<PQNode> fullChildren = new ArrayList<PQNode>();

        PQHelpers.collectChildrenByLabel(x, emptyChildren, fullChildren);

        //If there are no full nodes
        if (fullChildren.size() == 0) {
            return false;
        }
        //If there are no empty nodes
        if (emptyChildren.size() == 0) {
            return false;
        }
        //If there were other nodes than full or empty
        if ( fullChildren.size() + emptyChildren.size() != x.children.size()) {
            return false;
        }

        //Replacement phase

        x.labelType = PQNode.PARTIAL;
        x.nodeType = PQNode.QNODE;
        x.children = new ArrayList<>();

        // Alternative form B
        if(emptyChildren.size() == 1 && fullChildren.size() == 1){
            PQNode emptyNode = emptyChildren.get(0);
            PQNode fullNode = fullChildren.get(0);
            x.children.addAll(Arrays.asList(emptyNode, fullNode));
            setCircularLinks(x.children);
            emptyNode.parent = x;
            fullNode.parent = x;
            System.out.println("TEMPLATE P3 (alt form)");
            return true;
        }

        PQNode emptyPNode = new PQNode();
        PQNode fullPNode = new PQNode();

        emptyPNode.nodeType = PQNode.PNODE;
        fullPNode.nodeType = PQNode.PNODE;
        emptyPNode.labelType = PQNode.EMPTY;
        fullPNode.labelType = PQNode.FULL;

        emptyPNode.parent = x;
        fullPNode.parent = x;

        //Adding the children to the appropriate P node
        emptyPNode.children = emptyChildren;
        fullPNode.children = fullChildren;

        if(emptyChildren.size() == 1 && fullChildren.size() == 1){
            PQNode emptyChild = emptyChildren.get(0);
            emptyChild.parent = x;
            x.children.add(emptyChild);
            setCircularLinks(Arrays.asList(emptyChild, fullChildren.get(0)));

            PQNode fullChild = fullChildren.get(0);
            fullChild.parent = x;
            x.children.add(fullChild);
            setCircularLinks(Arrays.asList(emptyChildren.get(0), fullChild));
        }
        else if(emptyChildren.size() == 1 && fullChildren.size() > 1){
            PQNode emptyChild = emptyChildren.get(0);
            emptyChild.parent = x;
            x.children.add(emptyChild);
            setCircularLinks(Arrays.asList(emptyChild, fullPNode));

            for (PQNode child : fullChildren) {
                child.parent = fullPNode;
            }
            x.children.add(fullPNode);
            setCircularLinks(fullChildren);
        }
        else if(emptyChildren.size() > 1 && fullChildren.size() == 1){
            for (PQNode child : emptyChildren) {
                child.parent = emptyPNode;
            }
            x.children.add(emptyPNode);
            setCircularLinks(emptyChildren);

            PQNode fullChild = fullChildren.get(0);
            fullChild.parent = x;
            x.children.add(fullChild);
            setCircularLinks(Arrays.asList(emptyPNode, fullChild));
        }
        else {
            for (PQNode child : emptyChildren) {
                child.parent = emptyPNode;
            }
            x.children.add(emptyPNode);
            setCircularLinks(emptyChildren);

            for (PQNode child : fullChildren) {
                child.parent = fullPNode;
            }
            x.children.add(fullPNode);
            setCircularLinks(fullChildren);
        }

        for (PQNode n : x.endmostChildren()) {
            n.parent = x;
        }

        for (PQNode n : x.internalChildren()) {
            n.parent = null;
        }

        System.out.println("TEMPLATE P3");
        return true;
    }


    /**
     * Tries to match the tree at x (x must be the root of the pertinent subtree) to template P4, if successful,
     * we apply it.
     *
     * TEMPLATE:
     *
     *    -----------------------(P)-----------------------
     *    |      |                |                |      |
     *
     *    E .... E       --------[P]--------       F .... F
     *                   |      |   |      |
     *
     *                   E .... E   F .... F
     *
     * REPLACEMENT:
     *    ----------------------(P)----------------
     *    |        |                              |
     *
     *    E  ....  E               ---------------[P]----------------
     *                             |        |   |        |          |
     *
     *                             E  ....  E   F  ....  F     ----(F)---
     *                                                         |        |
     *
     *                                                         F  ....  F
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_P4(PQNode x){

        if(!x.nodeType.equals(PQNode.PNODE)){
            return false;
        }

        //Matching phase
        List<PQNode> emptyChildren = x.getChildrenOfLabel(PQNode.EMPTY);
        List<PQNode> fullChildren = x.getChildrenOfLabel(PQNode.FULL);
        List<PQNode> partialChildren = x.getChildrenOfLabel(PQNode.PARTIAL);

        //If there is not exactly 1 partial child
        if (partialChildren.size() != 1) {
            return false;
        }

        //If there were other nodes than full, empty or partial
        if ( fullChildren.size() + emptyChildren.size() + partialChildren.size() != x.children.size()) {
            return false;
        }

        //If partial node is not a Q node
        if (partialChildren.get(0).nodeType != PQNode.QNODE) {
            return false;
        }

        //Replacement phase

        x.children.removeAll(fullChildren);

        if(fullChildren.size() == 1){
            PQNode fullChild = fullChildren.get(0);


            fullChild.parent = partialChildren.get(0);

            // Add child to side of Q-Node that is not empty
            if(!partialChildren.get(0).endmostChildren().get(0).labelType.equals(PQNode.EMPTY)){
                partialChildren.get(0).children.add(0, fullChild);
            }
            else {
                partialChildren.get(0).children.add(fullChild);
            }

            setCircularLinks(partialChildren.get(0).children);
        }
        else {
            PQNode pNodeParent = new PQNode();
            pNodeParent.nodeType = PQNode.PNODE;
            pNodeParent.labelType = PQNode.FULL;
            pNodeParent.children = fullChildren;
            pNodeParent.parent = partialChildren.get(0);


            // Add child to side of Q-Node that is not empty
            if(!partialChildren.get(0).endmostChildren().get(0).labelType.equals(PQNode.EMPTY)){
                partialChildren.get(0).children.add(0, pNodeParent);
            }
            else {
                partialChildren.get(0).children.add(pNodeParent);
            }

            for (PQNode n : fullChildren) {
                n.parent = pNodeParent;
            }


            //Setting the circular links, otherwise the endmost children would still point to their former neighbours
            setCircularLinks(partialChildren.get(0).children);
            setCircularLinks(pNodeParent.children);
        }

        setCircularLinks(x.children);

        System.out.println("TEMPLATE P4");

        return true;
    }

    /**
     * Tries to match the subtree at x (x must NOT be the root of the pertinent subtree) to template P5, if successful,
     * we apply it.
     * Note: This case is very similiar to TEMPLATE_P4.
     *       The matching is nearly identical, the only different being that x cannot be a root.
     * TEMPLATE:
     *
     *    -----------------------(P)-----------------------
     *    |      |                |                |      |
     *
     *    E .... E       --------[P]--------       F .... F
     *                   |      |   |      |
     *
     *                   E .... E   F .... F
     *
     * REPLACEMENT:
     *    -----------------------[P]-------------------
     *       |           |      |   |       |         |
     *
     *   ---(E)--        E .... E   F .... F      ---(F)--
     *   |      |                                 |      |
     *
     *   E .... E                                 F .... F
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_P5(PQNode x){

        if(!x.nodeType.equals(PQNode.PNODE)){
            return false;
        }

        if(x.children.size() == 0){
            return false;
        }

        PQNode qNode = null;
        int qNodeCount = 0;
        List<PQNode> emptyChildList = new ArrayList<>();
        List<PQNode> fullChildList = new ArrayList<>();
        for(PQNode n : x.children){
            if(n.nodeType.equals(PQNode.QNODE) ){
                qNodeCount++;
                qNode = n;
                qNode.nodeType = PQNode.QNODE;
            }
            else if(n.labelType.equals(PQNode.EMPTY)){
                emptyChildList.add(n);
            }
            else if(n.labelType.equals(PQNode.FULL)){
                fullChildList.add(n);
            }
        }

        if(qNodeCount != 1 || !qNode.labelType.equals(PQNode.PARTIAL)){
            return false;
        }

        PQNode newEmptiesNode = new PQNode();
        PQNode newFullsNode = new PQNode();

        if(emptyChildList.size() > 0) {
            if (emptyChildList.size() == 1) {
                newEmptiesNode = emptyChildList.get(0);

            }
            else {

                newEmptiesNode.labelType = PQNode.EMPTY;
                newEmptiesNode.nodeType = PQNode.PNODE;

                for (PQNode n : emptyChildList) {
                    n.parent = newEmptiesNode;
                    newEmptiesNode.children.add(n);
                    //x.children.remove(e);
                }
            }
            PQHelpers.addNodesAsChildrenToQNode(Arrays.asList(newEmptiesNode), qNode);
        }

        if(fullChildList.size() > 0) {
            if (fullChildList.size() == 1) {
                newFullsNode = fullChildList.get(0);

            }
            else {
                newFullsNode.labelType = PQNode.FULL;
                newFullsNode.nodeType = PQNode.PNODE;

                for (PQNode n : fullChildList) {
                    n.parent = newFullsNode;
                    newFullsNode.children.add(n);
                    //x.children.remove(f);
                }

            }
            PQHelpers.addNodesAsChildrenToQNode(Arrays.asList(newFullsNode), qNode);
        }

        //PQHelpers.addNodesAsChildrenToQNode(Arrays.asList(newEmptiesNode), qNode);
        //PQHelpers.addNodesAsChildrenToQNode(Arrays.asList(newFullsNode), qNode);

        PQNode xParent = x.getParent();
        qNode.parent = xParent;
        if(xParent.nodeType.equals(PQNode.PNODE)){
            xParent.children.add(qNode);
            xParent.removeChildren(Arrays.asList(x));
        }
        else {
            PQHelpers.insertNodeIntoSameChildIndex(qNode, x, xParent);
            PQHelpers.insertNodeIntoCircularList(qNode, x.circularLink_prev, x.circularLink_next);
            xParent.removeChildren(Arrays.asList(x));
        }

        x = qNode;

        System.out.println("TEMPLATE P5");

        return true;
    }

    /**
     * Tries to match the tree at x (x must be the root of the pertinent subtree) to template P6, if successful,
     * we apply it.
     *
     * TEMPLATE:
     *
     *    --------------------------------(P)---------------------------------
     *    |      |                |                |      |                  |
     *
     *    E .... E       --------[P]--------       F .... F         --------[P]--------
     *                   |      |   |      |                        |      |   |      |
     *
     *                   E .... E   F .... F                        F .... F   E .... E
     *
     * REPLACEMENT:
     *    --------------------------------(P)-----------
     *    |      |                                     |
     *
     *    E .... E       -----------------------------[P]------------------------------
     *                   |      |   |      |           |            |      |   |      |
     *
     *                   E .... E   F .... F       ---(F)--        F .... F   E .... E
     *                                             |      |
     *
     *                                             F .... F
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */

    public boolean TEMPLATE_P6(PQNode x){

        /** Matching the template */
        if(!x.nodeType.equals(PQNode.PNODE)){
            return false;
        }

        if(x.children.size() == 0){
            return false;
        }

        /** Gather root children */
        PQNode qNode1 = null;
        PQNode qNode2 = null;
        int qNodeCount = 0;
        List<PQNode> emptyRootChildList = new ArrayList<>();
        List<PQNode> fullRootChildList = new ArrayList<>();
        for(PQNode n : x.children){
            if(n.nodeType.equals(PQNode.QNODE)){
                if(qNodeCount == 0){
                    qNode1 = n;
                    qNodeCount++;
                }
                else if(qNodeCount == 1){
                    qNode2 = n;
                    qNodeCount++;
                }
                else {
                    return false;
                }
            }
            else if(n.labelType.equals(PQNode.EMPTY)){
                emptyRootChildList.add(n);
            }
            else if(n.labelType.equals(PQNode.FULL)){
                fullRootChildList.add(n);
            }
        }

        if(qNode1 == null || qNode2 == null){
            return false;
        }
        if(!qNode1.labelType.equals(PQNode.PARTIAL) || !qNode2.labelType.equals(PQNode.PARTIAL)){
            return false;
        }
        if(qNode1.endmostChildren().size() != 2 || qNode2.endmostChildren().size() != 2){
            return false;
        }

        /** Gather qNode1 children */
        List<PQNode> emptyQNodeChildList1 = new ArrayList<>();
        List<PQNode> fullQNodeChildList1 = new ArrayList<>();
        gatherQNodeChildren(emptyQNodeChildList1, fullQNodeChildList1, qNode1);

        //If empty children are not consecutive
        if (!checkIfConsecutive( emptyQNodeChildList1 )) {
            return false;
        }

        //If full children are not consecutive
        if (!checkIfConsecutive(fullQNodeChildList1)) {
            return false;
        }

        /** Gather qNode2 children */
        List<PQNode> emptyQNodeChildList2 = new ArrayList<>();
        List<PQNode> fullQNodeChildList2 = new ArrayList<>();
        gatherQNodeChildren(emptyQNodeChildList2, fullQNodeChildList2, qNode2);

        //If empty children are not consecutive
        if (!checkIfConsecutive( emptyQNodeChildList2 )) {
            return false;
        }

        //If full children are not consecutive
        if (!checkIfConsecutive(fullQNodeChildList2)) {
            return false;
        }


        /**
         *  Applying the template
         * */

        /** Setup PNode */
        PQNode pNode = new PQNode();
        if(fullRootChildList.size() > 1) {
            pNode.labelType = PQNode.FULL;
            pNode.nodeType = PQNode.PNODE;

            setCircularLinks(fullRootChildList);
            fullRootChildList.forEach(n -> n.parent = pNode);
            pNode.children = fullRootChildList;
        }

        /** Reconfigure qNode1 */
        PQNode leftMost1 = qNode1.endmostChildren().get(0);
        PQNode rightMost1 = qNode1.endmostChildren().get(1);
        if(leftMost1.labelType.equals(PQNode.FULL) && rightMost1.labelType.equals(PQNode.EMPTY)){
            rotateQNode(qNode1);
            leftMost1 = qNode1.endmostChildren().get(0);
            rightMost1 = qNode1.endmostChildren().get(1);
        }

        /** Reconfigure qNode2 */
        PQNode leftMost2 = qNode2.endmostChildren().get(0);
        PQNode rightMost2 = qNode2.endmostChildren().get(1);
        if(leftMost2.labelType.equals(PQNode.EMPTY) && rightMost2.labelType.equals(PQNode.FULL)){
            rotateQNode(qNode2);
            leftMost2 = qNode2.endmostChildren().get(0);
            rightMost2 = qNode2.endmostChildren().get(1);
        }

        /** Reconfigure circular links for qNode1, pNode, qNode2
         * Could use setCircularLinks in  in PQHelper, but there is no need
         * to set all of the links. */
        rightMost2.circularLink_next = leftMost1;

        leftMost1.circularLink_prev = rightMost2;

        if(fullRootChildList.size() > 1){
            rightMost1.circularLink_next = pNode;
            pNode.circularLink_next = leftMost2;

            leftMost2.circularLink_prev = pNode;
            pNode.circularLink_prev = rightMost1;
        }
        else {
            rightMost1.circularLink_next = leftMost2;
            leftMost2.circularLink_prev = rightMost1;
        }

        /** Reconfigure qNode1, qNode2 parents */
        rightMost1.parent = null;
        leftMost2.parent = null;

        /** Setup merging QNode */
        PQNode mergingQNode = new PQNode();
        mergingQNode.labelType = PQNode.PARTIAL;
        mergingQNode.nodeType = PQNode.QNODE;

        // Simplify if x only has one child
        PQNode xParent = x.getParent();
        if(xParent != null){
            mergingQNode.parent = xParent;
            xParent.children.remove(x);

            PQHelpers.insertNodeIntoCircularList(mergingQNode, x, x);

            if(xParent.nodeType.equals(PQNode.QNODE)){
                PQNode traversal = xParent.endmostChildren().get(0);
                PQNode end = xParent.endmostChildren().get(1);
                int index = 0;
                while(traversal != end && traversal != x){
                    index++;
                }
                xParent.children.add(index, mergingQNode);
            }
            else {
                xParent.children.add(mergingQNode);
            }

        }
        else {
            mergingQNode.parent = x;

            /** Reconfigure root */
            x.children.removeAll(fullRootChildList);
            x.children.remove(qNode1);
            x.children.remove(qNode2);
            x.children.add(mergingQNode);


        }


        // This should be changed to add the whole lists rather than traversing.
        mergingQNode.setQNodeEndmostChildren(leftMost1, rightMost2);
        //PQNode traversal = leftMost1;
        //while(traversal.circularLink_next != leftMost1){
        //    mergingQNode.children.add(traversal);
        //    traversal = traversal.circularLink_next;
        //}

        System.out.println("TEMPLATE P6");

        return true;
    }


    /**
     * Tries to match the tree with root x to template Q1, if successful,
     * we apply it.
     *
     * TEMPLATE:
     * Case #1:           |Case #2:
     *        [E]         |        [E]
     *    |        |      |    |        |
     *                    |
     *    E  ....  E      |    F  ....  F
     *                    |
     * REPLACEMENT:       |
     *        [E]         |        [F]
     *    |        |      |    |        |
     *                    |
     *    E  ....  E      |    F  ....  F
     *                    |
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_Q1(PQNode x){
       if (x.nodeType.equals(PQNode.QNODE)) {
           //return GENERALIZED_TEMPLATE_1(x);
           return QNODE_TEMPLATE_1(x);
       }
       return false;
    }

    /**
     * Tries to match the tree/subtree at x to template Q2, if successful,
     * we apply it.
     *
     * TEMPLATE:
     *
     *    -----------------------[P]-----------------------
     *    |      |                |                |      |
     *
     *    E .... E       --------[P]--------       F .... F
     *                   |      |   |      |
     *
     *                   E .... E   F .... F
     *
     * REPLACEMENT:
     *    -----------------------[P]------------------------
     *    |      |       |      |   |       |       |      |
     *
     *    E .... E       E .... E   F .... F        F .... F
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_Q2(PQNode x){

        //Matching

        //If x is not a qNode
        if (x.nodeType != PQNode.QNODE) {
            return false;
        }

        //If empty children are not consecutive
        if (!checkIfConsecutive(x.getChildrenOfLabel(PQNode.EMPTY))) {
            return false;
        }

        //If full children are not consecutive
        if (!checkIfConsecutive(x.getChildrenOfLabel(PQNode.FULL))) {
            return false;
        }

        //Check if x is not singly partial
        if (x.getChildrenOfLabel(PQNode.PARTIAL).size() > 1) {
            return false;
        }

        if(x.getChildrenOfLabel(PQNode.PARTIAL).size() == 1) {
            //Check if partial node is not a qnode
            if (x.getChildrenOfLabel(PQNode.PARTIAL).get(0).nodeType != PQNode.QNODE) {
                return false;
            }

            //Check if the partial node's full children are not consecutive
            if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(0).getChildrenOfLabel(PQNode.FULL))) {
                return false;
            }

            //Check if the partial node's empty children are not consecutive
            if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(0).getChildrenOfLabel(PQNode.EMPTY))) {
                return false;
            }
        }
        else {
            // No reduction needed
            return true;
        }




        //Replacement
        PQNode partialNode = x.getChildrenOfLabel(PQNode.PARTIAL).get(0);

        x.labelType = PQNode.PARTIAL;

        //Move all children of the partial node to the root
        List<PQNode> replacementChildren = new ArrayList<PQNode>();
        replacementChildren.addAll(x.getChildrenOfLabel(PQNode.EMPTY));
        replacementChildren.addAll(partialNode.getChildrenOfLabel(PQNode.EMPTY));
        replacementChildren.addAll(partialNode.getChildrenOfLabel(PQNode.FULL));
        replacementChildren.addAll(x.getChildrenOfLabel(PQNode.FULL));

        //Delete partial child
        partialNode = null;

        //Reset circular links
        setCircularLinks(replacementChildren);

        x.children = replacementChildren;

        //Set parent links
        for (PQNode n : x.endmostChildren()) {
            n.parent = x;
        }

        for (PQNode n : x.internalChildren()) {
            n.parent = null;
        }

        System.out.println("TEMPLATE Q2");

        return true;
    }

    /**
     * Tries to match the tree at x (x must be the root of the pertinent subtree) to template P6, if successful,
     * we apply it.
     *
     * TEMPLATE:
     *
     *    --------------------------------------------[P]-----------------------------------------------
     *    |      |                |                |      |                  |                  |      |
     *
     *    E .... E       --------[P]--------       F .... F         --------[P]--------         E .... E
     *                   |      |   |      |                        |      |   |      |
     *
     *                   E .... E   F .... F                        F .... F   E .... E
     *
     * REPLACEMENT:
     *
     *    --------------------------------------------[P]--------------------------------------------------
     *    |      |       |      |       |      |      |      |       |      |       |      |       |      |
     *
     *    E .... E       E .... E       F .... F      F .... F       F .... F       E .... E       E .... E
     *
     * @param x the node which represents the root of the tree/subtree
     * @return whether or not x matches the template
     */
    public boolean TEMPLATE_Q3(PQNode x){

        //Matching phase

        //Check if not qnode
        if (x.nodeType != PQNode.QNODE) {
            return false;
        }

        //Check if x is not doubly partial
        if (x.getChildrenOfLabel(PQNode.PARTIAL).size() != 2) {
            return false;
        }

        //Check if partial nodes are not qnodes
        if (x.getChildrenOfLabel(PQNode.PARTIAL).get(0).nodeType != PQNode.QNODE) {
            return false;
        }
        if (x.getChildrenOfLabel(PQNode.PARTIAL).get(1).nodeType != PQNode.QNODE) {
            return false;
        }

        //Check if the partial node's full children are not consecutive
        if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(0).getChildrenOfLabel(PQNode.FULL))) {
            return false;
        }
        if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(1).getChildrenOfLabel(PQNode.FULL))) {
            return false;
        }

        //Check if the partial node's empty children are not consecutive
        if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(0).getChildrenOfLabel(PQNode.EMPTY))) {
            return false;
        }
        if (!PQHelpers.checkIfConsecutive(x.getChildrenOfLabel(PQNode.PARTIAL).get(1).getChildrenOfLabel(PQNode.EMPTY))) {
            return false;
        }

        List<PQNode> leftEmpties = new ArrayList<PQNode>();
        List<PQNode> rightEmpties = new ArrayList<PQNode>();

        int flips = 0;
        int cntr = 0;
        for (PQNode n : x.getChildren()) {
            cntr++;
            if (flips == 0) {
                if (n.labelType == PQNode.PARTIAL) {
                    flips++;
                }
                else if (n.labelType != PQNode.EMPTY) {
                    return false;
                }
                //If empty
                else {
                   leftEmpties.add(n);
                }
            }
            else if (flips == 1) {
                if (n.labelType == PQNode.FULL) {
                    flips++;
                }
                else if (n.labelType != PQNode.PARTIAL) {
                    return false;
                }
            }
            else if (flips == 2) {
                if (n.labelType == PQNode.PARTIAL) {
                    flips++;
                }
                else if (n.labelType != PQNode.FULL) {
                    return false;
                }
            }
            else if (flips == 3) {
                if (n.labelType == PQNode.EMPTY) {
                    flips++;
                    rightEmpties.add(n);
                }
                else if (n.labelType != PQNode.PARTIAL) {
                    return false;
                }
            }
            else {
                if (n.labelType != PQNode.EMPTY) {
                    return false;
                }
                //If empty
                else {
                    rightEmpties.add(n);
                }
            }
        }

        //Replacement phase

        x.labelType = PQNode.PARTIAL;
        List<PQNode> partials = x.getChildrenOfLabel(PQNode.PARTIAL);

        List<PQNode> replacementChildren = new ArrayList<PQNode>();
        replacementChildren.addAll(leftEmpties);
        replacementChildren.addAll(partials.get(0).getChildren());
        replacementChildren.addAll(x.getChildrenOfLabel(PQNode.FULL));
        replacementChildren.addAll(partials.get(1).getChildren());
        replacementChildren.addAll(rightEmpties);

        leftEmpties.get(0).parent = x;
        rightEmpties.get(rightEmpties.size()-1).parent = x;
        x.children = replacementChildren;
        setCircularLinks(replacementChildren);

        System.out.println("TEMPLATE Q3");

        return true;
    }

}
















