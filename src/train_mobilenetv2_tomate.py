# =========================================
# Entrenamiento con Transfer Learning
# MobileNetV2 - Detección de Plagas en Tomate
# =========================================

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, Dropout, GlobalAveragePooling2D
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau, ModelCheckpoint
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from sklearn.utils.class_weight import compute_class_weight
import numpy as np

# -----------------------------------------
# Rutas del dataset
# -----------------------------------------
ruta_entrenamiento = "../dataset/tomato/train_set"
ruta_prueba = "../dataset/tomato/test_set"

# -----------------------------------------
# Parámetros generales
# -----------------------------------------
TAMANO_IMAGEN = (224, 224)
TAMANO_LOTE = 32
NUMERO_CLASES = 5
EPOCHS = 20

print("Configurando generadores de imágenes...")

# -----------------------------------------
# Generadores con preprocess_input
# -----------------------------------------
generador_entrenamiento = ImageDataGenerator(
    preprocessing_function=preprocess_input,
    rotation_range=30,
    width_shift_range=0.2,
    height_shift_range=0.2,
    shear_range=0.2,
    zoom_range=0.2,
    horizontal_flip=True,
    fill_mode="nearest"
)

generador_prueba = ImageDataGenerator(
    preprocessing_function=preprocess_input
)

# -----------------------------------------
# Carga del dataset
# -----------------------------------------
datos_entrenamiento = generador_entrenamiento.flow_from_directory(
    ruta_entrenamiento,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical",
    shuffle=True
)

datos_prueba = generador_prueba.flow_from_directory(
    ruta_prueba,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical",
    shuffle=False
)

print("Clases detectadas:")
print(datos_entrenamiento.class_indices)

# -----------------------------------------
# Pesos por clase (balanceo)
# -----------------------------------------
clases = datos_entrenamiento.classes

pesos_clase = compute_class_weight(
    class_weight="balanced",
    classes=np.unique(clases),
    y=clases
)

pesos_clase_dict = dict(enumerate(pesos_clase))
print("Pesos por clase:", pesos_clase_dict)

# -----------------------------------------
# Construcción del modelo MobileNetV2
# -----------------------------------------
print("Cargando MobileNetV2 preentrenada...")

base_model = MobileNetV2(
    weights="imagenet",
    include_top=False,
    input_shape=(224, 224, 3)
)

# Congelamos la base
base_model.trainable = False

modelo = Sequential([
    base_model,
    GlobalAveragePooling2D(),
    Dense(128, activation="relu"),
    Dropout(0.5),
    Dense(NUMERO_CLASES, activation="softmax")
])

print("Modelo construido correctamente.")

# -----------------------------------------
# Compilación
# -----------------------------------------
modelo.compile(
    optimizer=Adam(learning_rate=0.001),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

print("Modelo compilado.")

# -----------------------------------------
# Callbacks
# -----------------------------------------
checkpoint = ModelCheckpoint(
    "mejor_modelo_tomate.keras",
    monitor="val_accuracy",
    save_best_only=True,
    mode="max",
    verbose=1
)

reduce_lr = ReduceLROnPlateau(
    monitor="val_loss",
    factor=0.2,
    patience=3,
    min_lr=0.00001,
    verbose=1
)

early_stop = EarlyStopping(
    monitor="val_loss",
    patience=5,
    restore_best_weights=True,
    verbose=1
)

callbacks = [checkpoint, reduce_lr, early_stop]

# -----------------------------------------
# Entrenamiento
# -----------------------------------------
print("Iniciando entrenamiento...")

historial = modelo.fit(
    datos_entrenamiento,
    steps_per_epoch=datos_entrenamiento.samples // TAMANO_LOTE,
    epochs=EPOCHS,
    validation_data=datos_prueba,
    validation_steps=datos_prueba.samples // TAMANO_LOTE,
    class_weight=pesos_clase_dict,
    callbacks=callbacks
)

print("Entrenamiento finalizado.")

# -----------------------------------------
# Guardar modelo final
# -----------------------------------------
modelo.save("modelo_mobilenetv2_tomate_plagas.h5")
print("Modelo guardado correctamente.")
