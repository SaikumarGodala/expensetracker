package com.saikumar.expensetracker

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import java.io.File
import kotlin.math.ln

// Data classes matching our JSON structure
data class TrainingData(
    val samples: List<Sample>
)

data class Sample(
    val merchant: String?,
    val text: String?,
    val category: String?,
    val label: String?,
    val sender: String? = null
)

data class ModelOutput(
    val metadata: Map<String, Any>,
    val priors: Map<String, Double>,
    val likelihoods: Map<String, Map<String, Double>>
)

class ModelTrainerTest {

    @Test
    fun trainModel() {
        val gson = Gson()
        
        // Locate files relative to 'app' directory (where tests usually run from)
        // Adjust logic to find project root if needed
        var currentDir = File(System.getProperty("user.dir") ?: ".")
        if (currentDir.name != "app") {
            currentDir = File(currentDir, "app")
        }
        
        val inputFile = File(currentDir, "src/main/java/com/saikumar/expensetracker/ml_training_data.json")
        val outputFile = File(currentDir, "src/main/assets/model.json")
        
        println("Working dir: ${currentDir.absolutePath}")
        println("Input file: ${inputFile.absolutePath}")
        
        if (!inputFile.exists()) {
            throw RuntimeException("Input file not found: ${inputFile.absolutePath}")
        }

        // 1. Load Data
        val jsonContent = inputFile.readText()
        // Handle both simple list or object with samples
        val samples = try {
             gson.fromJson(jsonContent, TrainingData::class.java).samples
        } catch (e: Exception) {
             // Fallback: list of samples
             val listType = object : TypeToken<List<Sample>>() {}.type
             gson.fromJson<List<Sample>>(jsonContent, listType)
        }

        println("Loaded ${samples.size} samples.")

        // 2. Preprocess & Train
        val documents = mutableListOf<Pair<List<String>, String>>()
        val allCategories = mutableSetOf<String>()
        val vocabulary = mutableSetOf<String>()

        samples.forEach { sample ->
            val text = sample.merchant ?: sample.text
            val category = sample.category ?: sample.label
            
            if (!text.isNullOrBlank() && !category.isNullOrBlank()) {
                val tokens = tokenize(text)
                if (tokens.isNotEmpty()) {
                    documents.add(tokens to category)
                    allCategories.add(category)
                    vocabulary.addAll(tokens)
                }
            }
        }

        println("Documents: ${documents.size}")
        println("Vocabulary: ${vocabulary.size}")
        println("Categories: ${allCategories.size}")

        // 3. Probabilities
        val classCounts = mutableMapOf<String, Int>()
        val classWordCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val classTotalWords = mutableMapOf<String, Int>()

        documents.forEach { (tokens, category) ->
            classCounts[category] = (classCounts[category] ?: 0) + 1
            
            val wordMap = classWordCounts.getOrPut(category) { mutableMapOf() }
            tokens.forEach { token ->
                wordMap[token] = (wordMap[token] ?: 0) + 1
                classTotalWords[category] = (classTotalWords[category] ?: 0) + 1
            }
        }

        val totalDocs = documents.size.toDouble()
        val logPriors = mutableMapOf<String, Double>()
        allCategories.forEach { cat ->
            val prob = (classCounts[cat] ?: 0) / totalDocs
            logPriors[cat] = ln(prob)
        }

        val logLikelihoods = mutableMapOf<String, MutableMap<String, Double>>()
        val vocabSize = vocabulary.size

        allCategories.forEach { cat ->
            val catLikelihoods = mutableMapOf<String, Double>()
            val denom = (classTotalWords[cat] ?: 0) + vocabSize
            
            vocabulary.forEach { word ->
                val count = classWordCounts[cat]?.get(word) ?: 0
                val prob = (count + 1).toDouble() / denom
                catLikelihoods[word] = ln(prob)
            }
            logLikelihoods[cat] = catLikelihoods
        }

        // 4. Export
        val model = ModelOutput(
            metadata = mapOf(
                "algorithm" to "NaiveBayes",
                "vocab_size" to vocabSize,
                "doc_count" to documents.size
            ),
            priors = logPriors,
            likelihoods = logLikelihoods
        )

        // Ensure assets dir exists
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(gson.toJson(model))
        
        println("Model saved to: ${outputFile.absolutePath}")
    }

    private fun tokenize(text: String): List<String> {
        // Simple alphanumeric tokenization
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
    }
}
