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
package es.uam.eps.ir.ranksys.novdiv.itemnovelty.reranking;

import es.uam.eps.ir.ranksys.core.IdDouble;
import es.uam.eps.ir.ranksys.core.Recommendation;
import es.uam.eps.ir.ranksys.novdiv.itemnovelty.ItemNovelty;
import es.uam.eps.ir.ranksys.core.util.Stats;
import es.uam.eps.ir.ranksys.fast.utils.topn.IntDoubleTopN;
import es.uam.eps.ir.ranksys.novdiv.reranking.PermutationReranker;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.List;

/**
 * Item Novelty re-ranker. It re-ranks the output of a recommendation by re-scoring through a linear combination of the relevance scores and the output of a {@link ItemNovelty}.
 *
 * S. Vargas. Novelty and diversity evaluation and enhancement in Recommender
 * Systems. PhD Thesis.
 *
 * @author Saúl Vargas (saul.vargas@uam.es)
 * @author Pablo Castells (pablo.castells@uam.es)
 *
 * @param <U> type of the users
 * @param <I> type of the items
 */
public class ItemNoveltyReranker<U, I> extends PermutationReranker<U, I> {

    private final double lambda;
    private final ItemNovelty<U, I> novelty;
    private final boolean norm;

    /**
     * Constructor.
     *
     * @param lambda trade-off between relevance and novelty
     * @param novelty item novelty model
     * @param norm normalize the relevance and novelty scores
     */
    public ItemNoveltyReranker(double lambda, ItemNovelty<U, I> novelty, boolean norm) {
        this.lambda = lambda;
        this.novelty = novelty;
        this.norm = norm;
    }

    @Override
    public int[] rerankPermutation(Recommendation<U, I> recommendation, int maxLength) {
        U user = recommendation.getUser();
        ItemNovelty.UserItemNoveltyModel<U, I> uinm = novelty.getModel(user);

        if (uinm == null) {
            return new int[0];
        }

        int N = maxLength;
        if (maxLength == 0) {
            N = recommendation.getItems().size();
        }

        if (lambda == 0.0) {
            return getBasePerm(Math.min(N, recommendation.getItems().size()));
        }

        Object2DoubleMap<I> novMap = new Object2DoubleOpenHashMap<>();
        Stats relStats = new Stats();
        Stats novStats = new Stats();
        recommendation.getItems().forEach(itemValue -> {
            double nov = uinm.novelty(itemValue.id);
            novMap.put(itemValue.id, nov);
            relStats.accept(itemValue.v);
            novStats.accept(nov);
        });

        IntDoubleTopN topN = new IntDoubleTopN(N);
        List<IdDouble<I>> list = recommendation.getItems();
        int M = list.size();
        for (int i = 0; i < list.size(); i++) {
            topN.add(M - i, value(list.get(i), relStats, novMap, novStats));
        }
        topN.sort();

        int[] perm = topN.reverseStream()
                .mapToInt(e -> M - e.getIntKey())
                .toArray();

        return perm;
    }

    /**
     * Returns the normalized value of a relevance or novelty score.
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

    /**
     * Re-scored value to determine the re-ranking.
     *
     * @param iv item-relevance pair from the input recommendation
     * @param relStats statistics about the relevance scores
     * @param novMap item-novelty pairs
     * @param novStats statistics about the novelty scores 
     * @return the new score resulting by a normalized linear combination 
     * between relevance and novelty
     */
    protected double value(IdDouble<I> iv, Stats relStats, Object2DoubleMap<I> novMap, Stats novStats) {
        return (1 - lambda) * norm(iv.v, relStats) + lambda * norm(novMap.getDouble(iv.id), novStats);
    }
}
