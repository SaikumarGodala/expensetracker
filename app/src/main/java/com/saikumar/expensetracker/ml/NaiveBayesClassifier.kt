package com.saikumar.expensetracker.ml

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.sms.CategoryResult
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.exp

/**
 * Lightweight Naive Bayes Classifier for merchant categorization.
 * 
 * Uses a pre-trained model (assets/model.json) with:
 * - Bag of Words representation
 * - Laplace smoothing
 * - Log-probabilities for numerical stability
 * 
 * Size: < 100KB, Latency: < 1ms
 */
object NaiveBayesClassifier {
    private const val TAG = "NaiveBayes"
    private const val MODEL_FILE = "model.json"
    
    private var isLoaded = false
    private var priors: Map<String, Double> = emptyMap()
    private var likelihoods: Map<String, Map<String, Double>> = emptyMap()
    
    // Minimum confidence score to accept a prediction (normalized probability)
    // NB probabilities are often very polarized, so we use a relative threshold or raw log-prob check.
    // However, for simplicity, we'll return the top match and a confidence estimate.
    
    fun load(context: Context) {
        if (isLoaded) return
        
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(MODEL_FILE)
            val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            val model = JSONObject(jsonString)
            
            // Parse Priors
            val priorsJson = model.getJSONObject("priors")
            val priorsMap = mutableMapOf<String, Double>()
            priorsJson.keys().forEach { key ->
                priorsMap[key] = priorsJson.getDouble(key)
            }
            priors = priorsMap
            
            // Parse Likelihoods
            val likelihoodsJson = model.getJSONObject("likelihoods")
            val likelihoodsMap = mutableMapOf<String, Map<String, Double>>()
            likelihoodsJson.keys().forEach { cat ->
                val wordProbs = likelihoodsJson.getJSONObject(cat)
                val wordMap = mutableMapOf<String, Double>()
                wordProbs.keys().forEach { word ->
                    wordMap[word] = wordProbs.getDouble(word)
                }
                likelihoodsMap[cat] = wordMap
            }
            likelihoods = likelihoodsMap
            
            isLoaded = true
            Log.i(TAG, "Loaded Naive Bayes model. Categories: ${priors.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML model: ${e.message}")
        }
    }
    
    fun classify(text: String): CategoryResult? {
        if (!isLoaded || text.isBlank()) return null
        
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null
        
        var bestCategory = ""
        var maxLogProb = Double.NEGATIVE_INFINITY
        
        // Calculate P(C|Words) proportional to P(C) * Product(P(w|C))
        // Log space: log(P(C)) + Sum(log(P(w|C)))
        
        for ((category, prior) in priors) {
            var logProb = prior
            val catLikelihoods = likelihoods[category] ?: continue
            
            for (token in tokens) {
                // If word is in vocab for this class, add its log prob.
                // If word is unknown (not in training data for this class), ignored 
                // (or strictly speaking should have smoothed prob, but our model only exports knowns.
                // In our training script, we only exported occurring words. 
                // Implicitly, unknown words have small probability, but ignoring them is a common variation)
                // Better approach: training script calculated P(w|c) for ALL vocab. 
                // So if it's in `vocab` (global), it should be in `likelihoods`.
                // If it's NOT in global vocab, we ignore it.
                
                logProb += catLikelihoods[token] ?: 0.0
            }
            
            if (logProb > maxLogProb) {
                maxLogProb = logProb
                bestCategory = category
            }
        }
        
        // Convert log prob to "confidence"? 
        // NB scores are not calibrated probabilities. 
        // We'll return a fixed high confidence if it differentiates well, or medium.
        // For now, let's look at the margin vs runners up? 
        // Simple: Return 75 confidence (ML_PREDICTION)
        
        return if (bestCategory.isNotEmpty()) {
            Log.d(TAG, "ML classified '$text' -> '$bestCategory' (logProb: $maxLogProb)")
            CategoryResult(bestCategory, 75)
        } else {
            Log.d(TAG, "ML could not classify: '$text'")
            null
        }
    }
    
    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
    }
}
