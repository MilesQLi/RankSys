/* 
 * Copyright (C) 2015 Information Retrieval Group at Universidad Autonoma
 * de Madrid, http://ir.ii.uam.es
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package es.uam.eps.ir.ranksys.novdiv.reranking;

import es.uam.eps.ir.ranksys.core.IdDouble;
import es.uam.eps.ir.ranksys.core.Recommendation;
import static es.uam.eps.ir.ranksys.novdiv.reranking.PermutationReranker.getBasePerm;
import es.uam.eps.ir.ranksys.core.util.Stats;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import static java.lang.Math.min;
import java.util.List;

/**
 * Linear combination re-ranker that combines the original score of the input 
 * recommendation and a novelty component.
 *
 * @author Saúl Vargas (saul.vargas@uam.es)
 * @author Pablo Castells (pablo.castells@uam.es)
 * 
 * @param <U> type of the users
 * @param <I> type of the items
 */
public abstract class LambdaReranker<U, I> extends GreedyReranker<U, I> {

    /**
     * Trade-off parameter of the linear combination.
     */
    protected final double lambda;
    private final boolean norm;

    /**
     * Constructor.
     *
     * @param lambda trade-off parameter of the linear combination
     * @param cutoff how many items are re-ranked by the greedy selection.
     * @param norm decides whether apply a normalization of the values of the
     * component at each selection step
     */
    public LambdaReranker(double lambda, int cutoff, boolean norm) {
        super(cutoff);
        this.lambda = lambda;
        this.norm = norm;
    }

    @Override
    public int[] rerankPermutation(Recommendation<U, I> recommendation, int maxLength) {
        if (lambda == 0.0) {
            return getBasePerm(min(maxLength, recommendation.getItems().size()));
        } else {
            return super.rerankPermutation(recommendation, maxLength);
        }
    }

    @Override
    protected abstract GreedyUserReranker<U, I> getUserReranker(Recommendation<U, I> recommendation, int maxLength);

    /**
     * User re-ranker for {@link LambdaReranker}.
     */
    protected abstract class LambdaUserReranker extends GreedyUserReranker<U, I> {

        /**
         * Statistics about relevance scores.
         */
        protected Stats relStats;

        /**
         * Statistics about novelty scores.
         */
        protected Stats novStats;

        /**
         * Map of the novelty of each item.
         */
        protected Object2DoubleMap<I> novMap;

        /**
         * Constructor.
         *
         * @param recommendation input recommendation
         * @param maxLength maximum length of the re-ranked recommendation
         */
        public LambdaUserReranker(Recommendation<U, I> recommendation, int maxLength) {
            super(recommendation, maxLength);
        }

        /**
         * Returns the normalized value of a relevance or novelty
         * score.
         *
         * @param score the relevance or novelty score
         * @param stats the relevance or novelty statistics
         * @return the normalized score
         */
        protected double norm(double score, Stats stats) {
            if (norm) {
                return (score - stats.getMean()) / stats.getStandardDeviation();
            } else {
                return score;
            }
        }
        
        @Override
        protected int selectItem(IntSortedSet remainingI, List<IdDouble<I>> list) {
            novMap = new Object2DoubleOpenHashMap<>();
            relStats = new Stats();
            novStats = new Stats();
            remainingI.forEach(i -> {
                IdDouble<I> itemValue = list.get(i);
                double nov = nov(itemValue);
                novMap.put(itemValue.id, nov);
                relStats.accept(itemValue.v);
                novStats.accept(nov);
            });
            return super.selectItem(remainingI, list);
        }

        @Override
        protected double value(IdDouble<I> iv) {
            return (1 - lambda) * norm(iv.v, relStats) + lambda * norm(novMap.getDouble(iv.id), novStats);
        }

        /**
         * Returns the novelty score of an item.
         *
         * @param iv item-relevance pair
         * @return the novelty of the item
         */
        protected abstract double nov(IdDouble<I> iv);

    }
}
