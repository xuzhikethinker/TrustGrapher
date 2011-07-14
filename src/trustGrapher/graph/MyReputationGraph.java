////////////////////////////////MyReputationGraph///////////////////////////////
package trustGrapher.graph;

import cu.repsystestbed.algorithms.ReputationAlgorithm;
import cu.repsystestbed.entities.Agent;
import cu.repsystestbed.graphs.ReputationEdgeFactory;
import cu.repsystestbed.graphs.ReputationGraph;
import java.util.Collection;
import org.jgrapht.graph.SimpleDirectedGraph;
import trustGrapher.visualizer.eventplayer.TrustLogEvent;
import utilities.ChatterBox;

/**
 * A  graph that displays each agent's trust towards other agents that it's had transactions with.
 * @author Andrew O'Hara
 */
public class MyReputationGraph extends TrustGraph {
    private ReputationAlgorithm alg = null;

//////////////////////////////////Constructor///////////////////////////////////
    /**
     * Creates a hidden graph.  The components of this graph are actually the ones beign displayed.
     */
    public MyReputationGraph() {
        super((SimpleDirectedGraph) new ReputationGraph(new ReputationEdgeFactory()));
        type = HIDDEN;
    }

    /**
     * Creates a visible graph.  This graph is only used by the hidden graph to see which of it's own components should be displayed.
     * @param hiddenGraph A reference to the hiddenGraph so that the reputation can be changed
     * @param alg The Reputation algorithm that will calculate the reputation towards each agent
     */
    public MyReputationGraph(ReputationAlgorithm alg) {
        super((SimpleDirectedGraph) new ReputationGraph(new ReputationEdgeFactory()));
        this.alg = alg;
        type = VISIBLE;
    }

//////////////////////////////////Accessors/////////////////////////////////////
    public ReputationAlgorithm getAlg() {
        return alg;
    }

///////////////////////////////////Methods//////////////////////////////////////

    public void feedback(MyReputationGraph hiddenGraph, MyReputationEdge refEdge) {
        int from = refEdge.getAssessor().id;
        int to = refEdge.getAssessee().id;
        int key = refEdge.getKey();
        double trustScore = 0.0;

        if (getVertexInGraph(from) == null) {
            addPeer(from);
        }
        if (getVertexInGraph(to) == null) {
            addPeer(to);
        }
        Agent assessor = getVertexInGraph(from);
        Agent assessee = getVertexInGraph(to);
        MyReputationEdge edge = (MyReputationEdge) findEdge(from, to);
        if (edge == null) {
            edge = new MyReputationEdge(assessor, assessee, key);
            addEdge(edge, assessor, assessee);
        }

        ChatterBox.print(assessor + " giving feedback to " + assessee);
        Collection<Agent> agents = hiddenGraph.getVertices();
        for (Agent src : agents) {
            for (Agent sink : agents) {
                if (!src.equals(sink)) {
                    try{
                        trustScore = getAlg().calculateTrustScore(src, sink);
                    }catch(Exception ex){
                        ChatterBox.debug(this, "feedback()", ex.getMessage());
                    }
                    if (trustScore < 0.0 || trustScore > 1.0) {
                        ChatterBox.error(this, "feedback()", "The trustScore was " + trustScore + ".  It needs to be [0,1]");
                    }
                    ChatterBox.print("Trying to set Edge " + src.toString() + " " + sink.toString() + " to " + trustScore);
                    edge = (MyReputationEdge) hiddenGraph.findEdge(src, sink);
                    if (edge == null){
                        //ChatterBox.print("Couldn't find edge.  Creating one...");
                        edge = createEdge(src, sink);
                        addEdge(edge, src, sink);
                        hiddenGraph.addEdge(edge, src, sink);
                    }
                    edge.setReputation(trustScore);
                }
            }
        }
    }

    private MyReputationEdge createEdge(Agent src, Agent sink){
        if (getVertexInGraph(src.id) == null) {
            addPeer(src.id);
        }
        if (getVertexInGraph(sink.id) == null) {
            addPeer(sink.id);
        }
        src = getVertexInGraph(src.id);
        sink = getVertexInGraph(sink.id);
        int key = edgecounter++;
        MyReputationEdge edge = new MyReputationEdge(src, sink, key);
        return edge;
    }

    public void unFeedback(MyReputationGraph hiddenGraph, MyReputationEdge repEdge) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void graphEvent(TrustLogEvent gev, boolean forward, TrustGraph referenceGraph) {
        if (type == HIDDEN){
            ChatterBox.error(this, "graphEvent()", "This graph is not a visible graph.");
            return;
        }
        MyReputationEdge refEdge = (MyReputationEdge) referenceGraph.findEdge(gev.getAssessor(), gev.getAssessee());
        if (refEdge == null){
            ChatterBox.error(this, "graphEvent()", "Could not find the edge in the hidden graph.");
        }
        if (forward){
            feedback((MyReputationGraph)referenceGraph, refEdge);
        }else{
            unFeedback((MyReputationGraph) referenceGraph,refEdge);
        }
    }

    /**
     * Creates an edge but does not yet add the feedback to it.  As the visible edges, are added, the feedbacks will be added to the hidden edges
     * @param gev	The Log event which needs to be handled.
     */
    public void graphConstructionEvent(TrustLogEvent gev) {
        if (type == VISIBLE){
            ChatterBox.error(this, "graphConstructionEvent()", "This graph is not a hidden graph.");
            return;
        }
        int from = gev.getAssessor();
        int to = gev.getAssessee();
        if (getVertexInGraph(from) == null) {
            addPeer(from);
        }
        if (getVertexInGraph(to) == null) {
            addPeer(to);
        }
        Agent assessor = getVertexInGraph(from);
        Agent assessee = getVertexInGraph(to);
        MyReputationEdge edge = (MyReputationEdge) findEdge(from, to);
        if (edge == null) {//If the edge doesn't  exist, add it
            try {
                edge = new MyReputationEdge(assessor, assessee, edgecounter++);
                addEdge(edge, edge.getAssessor(), edge.getAssessee());
            } catch (Exception ex) {
                ChatterBox.error(this, "feedback()", "Error creating edge: " + ex.getMessage());
            }
        }
    }
}
////////////////////////////////////////////////////////////////////////////////
