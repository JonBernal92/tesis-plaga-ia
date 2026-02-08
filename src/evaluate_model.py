import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from sklearn.metrics import classification_report, confusion_matrix
import numpy as np
import os

# Rutas del dataset
ruta_prueba = "../dataset/tomato/test_set"

# Parámetros del modelo y datos
TAMANO_IMAGEN = (224, 224)
TAMANO_LOTE = 32

print("Cargando modelo entrenado...")
modelo = tf.keras.models.load_model("modelo_tomate_plagas.h5")

print("Cargando dataset de prueba...")

generador_prueba = ImageDataGenerator(rescale=1.0 / 255)

datos_prueba = generador_prueba.flow_from_directory(
    ruta_prueba,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical",
    shuffle=False  # Muy importante para que las predicciones y etiquetas coincidan
)

print("Realizando predicciones...")

predicciones_prob = modelo.predict(datos_prueba)
predicciones = np.argmax(predicciones_prob, axis=1)

# Etiquetas verdaderas
etiquetas_verdaderas = datos_prueba.classes

# Obtener nombres de clases
nombres_clases = list(datos_prueba.class_indices.keys())

print("Matriz de confusión:")
print(confusion_matrix(etiquetas_verdaderas, predicciones))

print("\nReporte de clasificación:")
print(classification_report(etiquetas_verdaderas, predicciones, target_names=nombres_clases))
