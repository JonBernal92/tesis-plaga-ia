# =========================================
# Entrenamiento del modelo de detección de plagas
# Cultivo: Tomate
# =========================================

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator

# Rutas del dataset
ruta_entrenamiento = "../dataset/tomato/train_set"
ruta_prueba = "../dataset/tomato/test_set"


# Parámetros del modelo
TAMANO_IMAGEN = (224, 224)
TAMANO_LOTE = 32

print("Cargando dataset de entrenamiento y prueba...")

# Generador de imágenes para entrenamiento
generador_entrenamiento = ImageDataGenerator(
    rescale=1.0 / 255
)

# Generador de imágenes para prueba
generador_prueba = ImageDataGenerator(
    rescale=1.0 / 255
)

# Cargar imágenes desde carpetas
datos_entrenamiento = generador_entrenamiento.flow_from_directory(
    ruta_entrenamiento,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical"
)

datos_prueba = generador_prueba.flow_from_directory(
    ruta_prueba,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical"
)

print("Clases detectadas:")
print(datos_entrenamiento.class_indices)

print("Dataset cargado correctamente.")
