# =========================================
# Evaluación del modelo de detección de plagas
# Cultivo: Tomate
# =========================================

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import os

# -----------------------------------------
# Rutas
# -----------------------------------------
ruta_prueba = "../dataset/tomato/test_set"
ruta_modelo = "modelo_tomate_plagas.h5"

# -----------------------------------------
# Parámetros
# -----------------------------------------
TAMANO_IMAGEN = (224, 224)
TAMANO_LOTE = 32

# -----------------------------------------
# Cargar modelo entrenado
# -----------------------------------------
print("Cargando modelo entrenado...")
modelo = tf.keras.models.load_model(ruta_modelo)
print("Modelo cargado correctamente.")

# -----------------------------------------
# Cargar dataset de prueba
# -----------------------------------------
print("Cargando dataset de prueba...")

generador_prueba = ImageDataGenerator(rescale=1.0 / 255)

datos_prueba = generador_prueba.flow_from_directory(
    ruta_prueba,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical",
    shuffle=False  # IMPORTANTE para evaluación correcta
)

# -----------------------------------------
# Realizar predicciones
# -----------------------------------------
print("Realizando predicciones...")

predicciones_prob = modelo.predict(datos_prueba)
predicciones = np.argmax(predicciones_prob, axis=1)

# Etiquetas reales
etiquetas_reales = datos_prueba.classes

# Nombres de clases
nombres_clases = list(datos_prueba.class_indices.keys())

# -----------------------------------------
# Matriz de confusión
# -----------------------------------------
matriz = confusion_matrix(etiquetas_reales, predicciones)

print("Matriz de confusión:")
print(matriz)

plt.figure(figsize=(8, 6))
sns.heatmap(
    matriz,
    annot=True,
    fmt="d",
    cmap="Blues",
    xticklabels=nombres_clases,
    yticklabels=nombres_clases
)
plt.xlabel("Predicción")
plt.ylabel("Clase real")
plt.title("Matriz de Confusión - Detección de Plagas en Tomate")
plt.tight_layout()
plt.savefig("matriz_confusion.png")
plt.show()

# -----------------------------------------
# Reporte de clasificación
# -----------------------------------------
reporte = classification_report(
    etiquetas_reales,
    predicciones,
    target_names=nombres_clases
)

print("\nReporte de clasificación:")
print(reporte)

with open("reporte_clasificacion.txt", "w", encoding="utf-8") as archivo:
    archivo.write(reporte)

print("Reporte guardado en 'reporte_clasificacion.txt'")
print("Matriz de confusión guardada en 'matriz_confusion.png'")
print("Evaluación finalizada correctamente.")
