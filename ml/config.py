SAMPLE_RATE = 200          # SisFall sampling rate (Hz)
WINDOW_SECONDS = 2.0       # Window length
WINDOW_SIZE = int(SAMPLE_RATE * WINDOW_SECONDS)  # 400 samples
STRIDE = 100               # Sliding window stride (50% overlap on 2s window)
NUM_AXES = 3               # ax, ay, az

NUM_CLASSES = 2            # 0 = NoFall (ADL), 1 = Fall
CLASS_NAMES = ["NoFall", "Fall"]

# CNN architecture
CONV1_FILTERS = 32
CONV1_KERNEL = 5
CONV2_FILTERS = 64
CONV2_KERNEL = 5
CONV3_FILTERS = 128
CONV3_KERNEL = 3
DENSE_UNITS = 64
DROPOUT = 0.5

# Training
BATCH_SIZE = 64
EPOCHS = 50
LEARNING_RATE = 0.001
VALIDATION_SPLIT = 0.2
TEST_SPLIT = 0.15
RANDOM_SEED = 42

# Paths
SISFALL_URLS = {
    "gdrive": "1-E-TLd5_J-DDWZXkuYL-moMpoezlMn4Z",
    "kaggle": "nvnikhil0001/sis-fall-original-dataset",
}

G_TO_MS2 = 9.81
