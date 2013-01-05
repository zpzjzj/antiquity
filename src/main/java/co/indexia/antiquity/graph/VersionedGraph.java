/**
 * Copyright (c) 2012-2013 "Indexia Technologies, ltd."
 *
 * This file is part of Antiquity.
 *
 * Antiquity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.indexia.antiquity.graph;

import java.util.Map;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.ElementHelper;
import com.tinkerpop.blueprints.util.wrappers.event.EventElement;
import com.tinkerpop.blueprints.util.wrappers.event.EventGraph;
import com.tinkerpop.blueprints.util.wrappers.event.EventVertex;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;
import co.indexia.antiquity.range.Range;

/**
 * Versioned graph implementation,
 * 
 * <p>
 * The underline graph must support transactions.
 * </p>
 * 
 * @see {@link Graph}
 * @see {@link TransactionalGraph}
 */
public abstract class VersionedGraph<T extends Graph, V extends Comparable<V>> extends EventGraph<T> implements GraphChangedListener {
	/**
	 * The property key which stores the last graph version
	 */
	protected static final String LATEST_GRAPH_VERSION_PROP_KEY = "__LATEST_GRAPH_VERSION__";
	/**
	 * A marker property key which indicates that the {@link Element} is removed.
	 */
	public static final String REMOVED_PROP_KEY = "__REMOVED__";
	/**
	 * A property key which holds the minimum valid version of this element.
	 */
	public static final String VALID_MIN_VERSION_PROP_KEY = "__VALID_MIN_VERSION__";
	/**
	 * A property key which holds the maximum valid version of this element.
	 */
	public static final String VALID_MAX_VERSION_PROP_KEY = "__VALID_MAX_VERSION__";

	/**
	 * The identifier of the graph configuration vertex
	 */
	public static final String GRAPH_CONF_VERTEX_ID = "VERSIONED_GRAPH_CONF_VERTEX";

	/**
	 * The label name of the edge which creates the chain of a vertex revisions 
	 */
	public static final String PREV_VERSION_CHAIN_EDGE_TYPE = "PREV_VERSION";

	/**
	 * The next version of the graph to be committed.
	 */
	private V nextGraphVersion = null;

	/**
	 * Create an instance of {@link VersionedGraph} with the specified underline {@link Graph}.
	 * 
	 * @param baseGraph
	 *            The underline base graph
	 */
	public VersionedGraph(T baseGraph) {
		super(baseGraph);
		addListener(this);

		// TODO: A better approach to do that
		// Create the conf vertex if it does not exist
		if (getVersionConfVertex() == null) {
			Vertex v = baseGraph.addVertex(GRAPH_CONF_VERTEX_ID);
			v.setProperty("GRAPH_CONF_VERTEX_ID", "GRAPH_CONF_VERTEX_ID");
		}
	}

	// Operation Overrides
	// --------------------------------------------------------------
	@Override
	public void removeVertex(final Vertex vertex) {
		raiseExceptionIfNotEventElement(vertex);

		this.onVertexRemoved(vertex);
	}

	@Override
	public void removeEdge(final Edge edge) {
		raiseExceptionIfNotEventElement(edge);

		this.onEdgeRemoved(edge);
	}

	/**
	 * Raise an exception of the specified {@link Element} does not support events.
	 * 
	 * @param element The element to be checked
	 */
	private void raiseExceptionIfNotEventElement(Element element) {
		if (!(element instanceof EventVertex)) {
			throw new IllegalArgumentException(String.format("Element [%s] does not support events.", element));
		}
	}

	// Get/Set Version Methods
	// --------------------------------------------------------------
	/**
	 * Set a version range of the specified element.
	 * 
	 * @param versionedElement The element to set the version range.
	 * @param range The version {@link Range}
	 */
	public void setVersion(Element versionedElement, Range<V> range)
	{
		setStartVersion(versionedElement, range.min());
		setEndVersion(versionedElement, range.max());
	}

	/**
	 * Set the start version range of the specified element.
	 * 
	 * @param versionedElement The element to set the start version range.
	 * @param startVersion The start version to set
	 */
	public void setStartVersion(Element versionedElement, V startVersion)
	{
		//TODO: A more appropriate way to handle element types
		Element e = versionedElement;
		
		if (versionedElement instanceof EventElement)
			e = ((EventElement)versionedElement).getBaseElement();
		
		e.setProperty(VALID_MIN_VERSION_PROP_KEY, startVersion);
	}

	/**
	 * Set the end version range of the specified element.
	 * 
	 * @param versionedElement The element to set the end version range.
	 * @param endVersion The end version to set
	 */
	public void setEndVersion(Element versionedElement, V endVersion)
	{
		//TODO: A more appropriate way to handle element types
		Element e = versionedElement;
		
		if (versionedElement instanceof EventElement)
			e = ((EventElement)versionedElement).getBaseElement();
		
		
		e.setProperty(VALID_MAX_VERSION_PROP_KEY, endVersion);
	}

	/**
	 * Get the start version of the specified element
	 * 
	 * @param versionedElement The element to get the start version.
	 * @return The start version of the specified element.
	 */
	@SuppressWarnings("unchecked")
	public V getStartVersion(Element versionedElement)
	{
		return (V) versionedElement.getProperty(VALID_MIN_VERSION_PROP_KEY);
	}

	/**
	 * Get the end version of the specified element
	 * 
	 * @param versionedElement The element to get the end version.
	 * @return The end version of the specified element.
	 */
	@SuppressWarnings("unchecked")
	public V getEndVersion(Element versionedElement)
	{
		return (V) versionedElement.getProperty(VALID_MAX_VERSION_PROP_KEY);
	}

	/**
	 * Get the version range of the specified element.
	 * 
	 * @param versionedElement The element to get the version range for.
	 * @return a {@link Range} of version of the specified element.
	 */
	public Range<V> getVersionRange(Element versionedElement) {
		return Range.range(getStartVersion(versionedElement), getEndVersion(versionedElement));
	}

	/**
	 * Determine whether the specified version is the start version of the specified element.
	 * 
	 * @param version The version to determine as the start of the version range.
	 * @param versionedElement The element to check
	 * @return true if the specified version is the start version of the specified element.
	 */
	public boolean isStartVersion(V version, Element versionedElement) {
		return version.equals(getStartVersion(versionedElement));
	}

	/**
	 * Determine whether the specified version is the end version of the specified element.
	 * 
	 * @param version The version to determine as the end of the version range.
	 * @param versionedElement The element to check
	 * @return true if the specified version is the end version of the specified element.
	 */
	public boolean isEndVersion(V version, Element versionedElement) {
		return version.equals(getEndVersion(versionedElement));
	}

	// Graph Version Methods
	// --------------------------------------------------------------
	/**
	 * Set the latest graph version.
	 * 
	 * @param newVersionToBeCommitted
	 *            The new version to be committed to set.
	 */
	protected abstract void setLatestGraphVersion(V newVersionToBeCommitted);

	/**
	 * Get the current graph version.
	 * 
	 * <p>
	 * Graph latest version is stored in the graph version configuration vertex
	 * <p>
	 * 
	 * @see {@link #getVersionConfVertex()}
	 * 
	 * @return The current graph version.
	 */
	protected abstract V getLatestGraphVersionImpl();

	protected V getLatestGraphVersion() {
		return getLatestGraphVersionImpl();
	}

	/**
	 * Get the next graph version
	 * 
	 * <p>
	 * Note: This method must be thread safe, there should never be two concurrent calls that return the same version.
	 * </p>
	 * 
	 * @return The next graph version
	 */
	protected abstract V getNextGraphVersionImpl();

	protected V getNextGraphVersion() {
		// TODO: Lock the configuration vertex
		if ((nextGraphVersion == null) || (!(this instanceof TransactionalGraph))) {
			nextGraphVersion = getNextGraphVersionImpl();
		}

		setLatestGraphVersion(nextGraphVersion);

		// TODO: Unlock the configuration vertex
		return nextGraphVersion;
	}

	/**
	 * Get the maximum possible graph version.
	 * 
	 * @return The maximum possible graph version
	 */
	protected abstract V getMaxPossibleGraphVersion();

	/**
	 * Get the version configuration vertex.
	 * 
	 * <p>
	 * Configuration vertex is queried very often and recommended to be cached.
	 * </p>
	 * 
	 * @return
	 */
	protected Vertex getVersionConfVertex() {
		Vertex v = getBaseGraph().getVertex(GRAPH_CONF_VERTEX_ID);

		if (v == null) {
			Iterable<Vertex> vs = getBaseGraph().getVertices("GRAPH_CONF_VERTEX_ID", "GRAPH_CONF_VERTEX_ID");

			if (vs.iterator().hasNext()) {
				v = vs.iterator().next();
				return v;
			}
			else {
				return null;
			}
		} else {
			return v;
		}

		// if (v == null)
		// throw new RuntimeException("Could not find the graph configuration vertex");
	}

	// Graph Versioning
	// --------------------------------------------------------------
	/**
	 * Version vertices in the graph.
	 * 
	 * @param version
	 *            The graph version that created the specified vertices
	 * @param vertices
	 *            The vertices to be versioned.
	 */
	protected void versionAddedVertices(V version, Iterable<Vertex> vertices) {
		Range<V> range = Range.range(version, getMaxPossibleGraphVersion());

		for (Vertex v : vertices) {
			setVersion(v, range);
		}
	}

	/**
	 * Version edges in the graph.
	 * 
	 * @param version
	 *            The graph version that created the specified edges
	 * @param edges
	 *            The edges to be versioned.
	 */
	protected void versionAddedEdges(V version, Iterable<Edge> edges) {
		Range<V> range = Range.range(version, getMaxPossibleGraphVersion());

		for (Edge e : edges) {
			setVersion(e, range);
		}
	}

	protected void versionRemovedVertices(V version, Iterable<Vertex> vertices) {
		for (Vertex v : vertices) {
			v.setProperty(REMOVED_PROP_KEY, getNextGraphVersion());
			v.setProperty(VALID_MAX_VERSION_PROP_KEY, getLatestGraphVersion());
		}
	}

	protected void versionRemovedEdges(V version, Iterable<Edge> edges) {
		for (Edge e : edges) {
			e.setProperty(REMOVED_PROP_KEY, getNextGraphVersion());
			e.setProperty(VALID_MAX_VERSION_PROP_KEY, getLatestGraphVersion());
		}
	}

	/**
	 * Returns whether the specified property key is used internally by the versioned graph or not.
	 * 
	 * @param key
	 *            The property key to determine
	 * @return true if property is for internal usage only
	 */
	private boolean isPropertyInternal(String key) {
		return (LATEST_GRAPH_VERSION_PROP_KEY.equals(key) || (REMOVED_PROP_KEY.equals(key))
				|| (VALID_MIN_VERSION_PROP_KEY.equals(key)) || (VALID_MAX_VERSION_PROP_KEY.equals(key)));
	}

	/**
	 * Create a historical vertex which contains the modified vertex content before it was modified.
	 * 
	 * @param modifiedVertex
	 *            The modified versioned vertex.
	 * @param oldValues
	 *            The old properties values of the modified versioned vertex.
	 * @return {@link Vertex} containing the old data before the vertex was modified.
	 */
	private Vertex createHistoricalVertex(Vertex modifiedVertex, Map<String, Object> oldValues) {
		// TODO: Auto identifier?
		Vertex hv = getBaseGraph().addVertex(modifiedVertex.getId()+"-"+getNextGraphVersion());
		ElementHelper.copyProperties(modifiedVertex, hv);

		for (Map.Entry<String, Object> prop : oldValues.entrySet())
		{
			String key = prop.getKey();
			Object value = prop.getValue();
			if (value == null) {
				hv.removeProperty(key);
			}
			else {
				hv.setProperty(key, value);
			}
		}

		return hv;
	}

	/**
	 * Add the historical vertex in the vertex versions chain
	 * 
	 * @param latestGraphVersion
	 *            The latest graph version
	 * @param newVersion
	 *            The new version to be committed
	 * @param modifiedVertex
	 *            The modified vertex
	 * @param newHistoricalVertex
	 *            The historical vertex of the modified vertex
	 */
	private void addHistoricalVertexInChain(V latestGraphVersion,
			V newVersion,
			Vertex modifiedVertex,
			Vertex newHistoricalVertex) {
		Iterable<Edge> currEdges = modifiedVertex.getEdges(Direction.OUT, PREV_VERSION_CHAIN_EDGE_TYPE);

		if (currEdges.iterator().hasNext()) {
			Edge currEdge = currEdges.iterator().next();
			if (currEdges.iterator().hasNext())
				throw new IllegalStateException("Multiple versioned edges in vertex chain exist");

			// TODO: Edge id?
			getBaseGraph().addEdge(null,
					modifiedVertex,
					currEdges.iterator().next().getVertex(Direction.OUT),
					PREV_VERSION_CHAIN_EDGE_TYPE);
			getBaseGraph().removeEdge(currEdge);
		}

		// TODO: Edge id?
		getBaseGraph().addEdge(null, newHistoricalVertex, ((EventVertex)modifiedVertex).getBaseVertex(), PREV_VERSION_CHAIN_EDGE_TYPE);
		setStartVersion(newHistoricalVertex, getStartVersion(modifiedVertex));
		setEndVersion(newHistoricalVertex, latestGraphVersion);
		setStartVersion(modifiedVertex, newVersion);
	}

	/**
	 * Version the specified modified vertices.
	 * 
	 * @param latestGraphVersion
	 *            The latest graph version
	 * @param newVersion
	 *            The new version to be committed
	 * @param vertex
	 *            The modified vertex
	 * @param oldValues
	 *            The old properties values of the modified vertex
	 * 
	 * @return The historical created vertex
	 */
	protected Vertex versionModifiedVertex(V latestGraphVersion,
			V newVersion,
			Vertex vertex,
			Map<String, Object> oldValues) {

		Vertex historicalV = createHistoricalVertex(vertex, oldValues);
		addHistoricalVertexInChain(latestGraphVersion, newVersion, vertex, historicalV);

		return historicalV;
	}
}