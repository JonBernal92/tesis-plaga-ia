# =========================================
# Entrenamiento del modelo de detección de plagas
# Cultivo: Tomate
# =========================================

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout
from tensorflow.keras.optimizers import Adam
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
CANALES = 3
NUMERO_CLASES = 5

print("Cargando dataset de entrenamiento y prueba...")

# -----------------------------------------
# Generadores de imágenes con aumento para entrenamiento
# -----------------------------------------
generador_entrenamiento = ImageDataGenerator(
    rescale=1.0 / 255,
    rotation_range=20,
    width_shift_range=0.2,
    height_shift_range=0.2,
    shear_range=0.15,
    zoom_range=0.15,
    horizontal_flip=True,
    fill_mode="nearest"
)

generador_prueba = ImageDataGenerator(
    rescale=1.0 / 255
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
print("Dataset cargado correctamente.")

# -----------------------------------------
# Cálculo de pesos para clases desbalanceadas
# -----------------------------------------
clases = list(datos_entrenamiento.class_indices.keys())
clases_entrenamiento = datos_entrenamiento.classes
pesos_clase = compute_class_weight(
    class_weight="balanced",
    classes=np.unique(clases_entrenamiento),
    y=clases_entrenamiento
)
pesos_clase_dict = dict(enumerate(pesos_clase))
print("Pesos por clase calculados:")
for i, clase in enumerate(clases):
    print(f"Clase '{clase}': peso {pesos_clase_dict[i]:.3f}")

# -----------------------------------------
# Construcción del modelo CNN
# -----------------------------------------
print("Construyendo el modelo CNN...")

modelo = Sequential()

# Capa convolucional 1
modelo.add(Conv2D(
    32,
    (3, 3),
    activation="relu",
    input_shape=(TAMANO_IMAGEN[0], TAMANO_IMAGEN[1], CANALES)
))
modelo.add(MaxPooling2D(pool_size=(2, 2)))

# Capa convolucional 2
modelo.add(Conv2D(64, (3, 3), activation="relu"))
modelo.add(MaxPooling2D(pool_size=(2, 2)))

# Capa convolucional 3
modelo.add(Conv2D(128, (3, 3), activation="relu"))
modelo.add(MaxPooling2D(pool_size=(2, 2)))

# Aplanado
modelo.add(Flatten())

# Capas densas
modelo.add(Dense(128, activation="relu"))
modelo.add(Dropout(0.5))

# Capa de salida
modelo.add(Dense(NUMERO_CLASES, activation="softmax"))

print("Modelo CNN construido correctamente.")

# -----------------------------------------
# Compilación del modelo
# -----------------------------------------
modelo.compile(
    optimizer=Adam(learning_rate=0.0001),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

print("Modelo compilado correctamente.")

# -----------------------------------------
# Entrenamiento del modelo
# -----------------------------------------
EPOCHS = 15  # Puedes cambiar la cantidad de épocas luego

print("Iniciando entrenamiento del modelo...")

historial = modelo.fit(
    datos_entrenamiento,
    steps_per_epoch=datos_entrenamiento.samples // TAMANO_LOTE,
    epochs=EPOCHS,
    validation_data=datos_prueba,
    validation_steps=datos_prueba.samples // TAMANO_LOTE,
    class_weight=pesos_clase_dict  # <-- uso de pesos para mejorar clases minoritarias
)

print("Entrenamiento finalizado.")

# -----------------------------------------
# Guardar el modelo entrenado
# -----------------------------------------
modelo_guardado = "modelo_tomate_plagas.h5"
modelo.save(modelo_guardado)

print(f"Modelo guardado en archivo: {modelo_guardado}")
