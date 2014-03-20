/*
 * Copyright (C)2014, Novartis Institutes for BioMedical Research Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 * 
 * - Neither the name of Novartis Institutes for BioMedical Research Inc.
 *   nor the names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rdkit.lucene;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.PriorityQueue;

final class SubstructureHitQueue extends PriorityQueue<ScoreDoc> {

	private final boolean prePopulate;

	/**
	 * Creates a new instance with <code>size</code> elements. If
	 * <code>prePopulate</code> is set to true, the queue will pre-populate itself
	 * with sentinel objects and set its {@link #size()} to <code>size</code>. In
	 * that case, you should not rely on {@link #size()} to get the number of
	 * actual elements that were added to the queue, but keep track yourself.<br>
	 * <b>NOTE:</b> in case <code>prePopulate</code> is true, you should pop
	 * elements from the queue using the following code example:
	 * 
	 * <pre>
	 * PriorityQueue pq = new HitQueue(10, true); // pre-populate.
	 * ScoreDoc top = pq.top();
	 * 
	 * // Add/Update one element.
	 * top.score = 1.0f;
	 * top.doc = 0;
	 * top = (ScoreDoc) pq.updateTop();
	 * int totalHits = 1;
	 * 
	 * // Now pop only the elements that were *truly* inserted.
	 * // First, pop all the sentinel elements (there are pq.size() - totalHits).
	 * for (int i = pq.size() - totalHits; i &gt; 0; i--) pq.pop();
	 * 
	 * // Now pop the truly added elements.
	 * ScoreDoc[] results = new ScoreDoc[totalHits];
	 * for (int i = totalHits - 1; i &gt;= 0; i--) {
	 *   results[i] = (ScoreDoc) pq.pop();
	 * }
	 * </pre>
	 * 
	 * <p><b>NOTE</b>: This class pre-allocate a full array of
	 * length <code>size</code>.
	 * 
	 * @param size
	 *          the requested size of this queue.
	 * @param prePopulate
	 *          specifies whether to pre-populate the queue with sentinel values.
	 * @see #getSentinelObject()
	 */
	SubstructureHitQueue(final int size, final boolean prePopulate) {
		this.prePopulate = prePopulate;
		initialize(size);
	}

	// Returns null if prePopulate is false.
	@Override
	protected ScoreDoc getSentinelObject() {
		// Always set the doc Id to MAX_VALUE so that it won't be favored by
		// lessThan. This generally should not happen since if score is not NEG_INF,
		// TopScoreDocCollector will always add the object to the queue.
		return !prePopulate ? null : new ScoreDoc(Integer.MAX_VALUE, Float.NEGATIVE_INFINITY);
	}

	@Override
	protected final boolean lessThan(final ScoreDoc hitA, final ScoreDoc hitB) {
		if (hitA.score == hitB.score)
			return hitA.doc > hitB.doc;
			else
				return hitA.score < hitB.score;
	}
}
