# ML Merchant Classifier

Hybrid ML pipeline to extract and classify merchants from SMS logs.

## Pipeline Architecture (Hybrid)

1.  **Regex Pre-filter**: Instantly categorizes defined types (Salary, P2P, Refund) with 100% precision.
2.  **Merchant Extraction**: Extracts merchant names (e.g. "SWIGGY") using regex.
3.  **Zero-Shot ML**: Uses `MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli` on GPU for semantic classification of the remaining items.
    *   **Input**: "A financial transaction purchase at {merchant}" OR "A financial transaction related to {text}"
    *   **Batching**: Processes in batches of 32 for efficiency.

## Requirements

- Python 3.10+
- NVIDIA GPU with CUDA (Recommended)
- ~2GB disk space for models

## Setup

```powershell
# Create virtual environment
python -m venv venv
.\venv\Scripts\Activate.ps1

# Install dependencies (with CUDA)
pip install torch --index-url https://download.pytorch.org/whl/cu121
pip install -r requirements.txt
```

## Usage

### 1. Run the Classifier

```powershell
# Full hybrid pipeline (uses GPU)
python merchant_classifier.py -i raw_sms_inbox_scan.jsonl -o merchant_training_data.json

# Extract only (no ML)
python merchant_classifier.py -i raw_sms_inbox_scan.jsonl -o merchant_training_data.json --skip-ml
```

### 2. Output Files

- `merchant_training_data.json`: Full training dataset with `text`, `merchant`, `category`, `confidence`, and `decision_reason`.
- `merchant_categories.kt`: Auto-generated Kotlin map code to paste into `CategoryMapper.kt`.

## Output JSON Format

```json
{
  "text": "Sent Rs.100 to SWIGGY...",
  "merchant": "SWIGGY",
  "predicted_category": "Food Delivery",
  "confidence": 0.98,
  "method": "ML",
  "decision_reason": "ML (Zero-shot)"
}
```
