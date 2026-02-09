# =========================================
# Evaluación del modelo - MobileNetV2
# Detección de Plagas en Tomate
# =========================================

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt
import numpy as np

# -----------------------------------------
# Rutas
# -----------------------------------------
ruta_prueba = "../dataset/tomato/test_set"
ruta_modelo = "modelo_mobilenetv2_tomate_plagas.h5"

# -----------------------------------------
# Parámetros
# -----------------------------------------
TAMANO_IMAGEN = (224, 224)
TAMANO_LOTE = 32

# -----------------------------------------
# Cargar modelo
# -----------------------------------------
print("Cargando modelo entrenado...")
modelo = tf.keras.models.load_model(ruta_modelo)
print("Modelo cargado correctamente.")

# -----------------------------------------
# Generador de prueba (MobileNetV2)
# -----------------------------------------
print("Cargando dataset de prueba...")

generador_prueba = ImageDataGenerator(
    preprocessing_function=preprocess_input
)

datos_prueba = generador_prueba.flow_from_directory(
    ruta_prueba,
    target_size=TAMANO_IMAGEN,
    batch_size=TAMANO_LOTE,
    class_mode="categorical",
    shuffle=False
)

# -----------------------------------------
# Predicciones
# -----------------------------------------
print("Realizando predicciones...")

predicciones_prob = modelo.predict(datos_prueba)
predicciones = np.argmax(predicciones_prob, axis=1)

etiquetas_reales = datos_prueba.classes
nombres_clases = list(datos_prueba.class_indices.keys())

# -----------------------------------------
# Matriz de confusión
# -----------------------------------------
matriz = confusion_matrix(etiquetas_reales, predicciones)

print("Matriz de confusión:")
print(matriz)

plt.figure(figsize=(8, 6))
plt.imshow(matriz, interpolation="nearest")
plt.title("Matriz de Confusión - Plagas en Tomate")
plt.colorbar()

ticks = np.arange(len(nombres_clases))
plt.xticks(ticks, nombres_clases, rotation=45)
plt.yticks(ticks, nombres_clases)

plt.xlabel("Predicción")
plt.ylabel("Clase real")

for i in range(matriz.shape[0]):
    for j in range(matriz.shape[1]):
        plt.text(j, i, matriz[i, j], ha="center", va="center")

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

with open("reporte_clasificacion.txt", "w", encoding="utf-8") as f:
    f.write(reporte)

print("Evaluación finalizada correctamente.")
print("✔ matriz_confusion.png generada")
print("✔ reporte_clasificacion.txt generado")
