
import json
import math
import collections
import re
import argparse
from typing import Dict, List, Tuple

def tokenize(text: str) -> List[str]:
    """Simple tokenization: lowercase, keep only alphanumeric."""
    text = text.lower()
    # Keep alphanumeric characters
    tokens = re.findall(r'[a-z0-9]+', text)
    return tokens

def train_naive_bayes(data_file: str, output_file: str):
    print(f"Loading data from {data_file}...")
    
    documents = []
    all_categories = set()
    vocabulary = set()
    
    # 1. Load Data
    with open(data_file, 'r', encoding='utf-8') as f:
        first_char = f.read(1)
        f.seek(0)
        
        # Detect format: JSON Object (App Export) vs JSONL (Raw Logs)
        source_records = []
        if first_char == '{':
            try:
                # Try parsing entire file as JSON
                full_json = json.load(f)
                if 'samples' in full_json and isinstance(full_json['samples'], list):
                    print(f"Detected App Export format with {len(full_json['samples'])} samples.")
                    source_records = full_json['samples']
                    # Optional: Print distribution of senders for debug
                    senders = [r.get('sender') for r in source_records if r.get('sender')]
                    print(f"Contains data from {len(set(senders))} unique senders.")
                else:
                    # Might be a single JSONL line that happens to be a valid object
                    f.seek(0)
                    for line in f:
                        if line.strip(): source_records.append(json.loads(line))
            except json.JSONDecodeError:
                # Fallback to JSONL
                f.seek(0)
                for line in f:
                     if line.strip(): source_records.append(json.loads(line))
        else:
             for line in f:
                 if line.strip(): source_records.append(json.loads(line))

    for record in source_records:
            # Use 'merchant' as the primary feature, maybe fallback to 'text' if needed?
            # The plan said "classify merchant names".
            # The training data has "merchant" field.
            
            text = record.get('merchant', '')
            if not text:
                text = record.get('text', '') # Fallback
                
            category = record.get('category') # App export uses 'category'
            if not category:
                category = record.get('label') # Log extraction uses 'label'
            
            if not category or not text: continue
            
            tokens = tokenize(text)
            if not tokens: continue
            
            documents.append((tokens, category))
            all_categories.add(category)
            vocabulary.update(tokens)
            
    print(f"Loaded {len(documents)} documents.")
    print(f"Vocabulary size: {len(vocabulary)}")
    print(f"Categories: {len(all_categories)}")
    
    # 2. Train (Calculate Counts)
    class_counts = collections.defaultdict(int)
    class_word_counts = collections.defaultdict(lambda: collections.defaultdict(int))
    class_total_words = collections.defaultdict(int)
    
    for tokens, category in documents:
        class_counts[category] += 1
        for token in tokens:
            class_word_counts[category][token] += 1
            class_total_words[category] += 1
            
    # 3. Calculate Log Probabilities (Smoothing)
    # P(Class)
    total_docs = len(documents)
    log_priors = {}
    for cat in all_categories:
        prob = class_counts[cat] / total_docs
        log_priors[cat] = math.log(prob)
        
    # P(Word | Class) with Laplace Smoothing (add-1)
    # prob = (count(w, c) + 1) / (count(c) + V)
    vocab_size = len(vocabulary)
    log_likelihoods = {}
    
    for cat in all_categories:
        log_likelihoods[cat] = {}
        denom = class_total_words[cat] + vocab_size
        
        # for every word in the GLOBAL vocabulary
        for word in vocabulary:
            count = class_word_counts[cat].get(word, 0)
            prob = (count + 1) / denom
            log_likelihoods[cat][word] = math.log(prob)
            
        # Also handle "unknown" words during inference?
        # Usually we ignore unknown words in standard NB or handle them with a specific smoothed value.
        # For this simple implementation, we'll only match words in vocab.
        
    # 4. Export Model
    model = {
        "metadata": {
            "algorithm": "NaiveBayes",
            "vocab_size": vocab_size,
            "doc_count": total_docs
        },
        "priors": log_priors,
        "likelihoods": log_likelihoods 
        # Structure: { category: { word: log_prob } }
    }
    
    print(f"Saving model to {output_file}...")
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=2)
        
    print("Done!")
    
    # Validation on training set (Self-test)
    correct = 0
    for tokens, true_cat in documents:
        best_cat = None
        best_score = -float('inf')
        
        for cat in all_categories:
            score = log_priors[cat]
            for token in tokens:
                if token in log_likelihoods[cat]:
                    score += log_likelihoods[cat][token]
            
            if score > best_score:
                best_score = score
                best_cat = cat
                
        if best_cat == true_cat:
            correct += 1
            
    print(f"Training Accuracy: {correct}/{total_docs} ({correct/total_docs:.2%})")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('input', help='Input JSONL file')
    parser.add_argument('output', help='Output Model JSON file')
    args = parser.parse_args()
    
    train_naive_bayes(args.input, args.output)
