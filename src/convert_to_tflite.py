# =========================================
# Conversión del modelo a TensorFlow Lite
# Proyecto: Detección de plagas en tomate
# =========================================

import tensorflow as tf
import os

# -----------------------------------------
# Rutas
# -----------------------------------------
MODELO_ENTRENADO = "modelo_tomate_plagas.h5"
MODELO_TFLITE = "modelo_tomate_plagas.tflite"

print("Cargando modelo entrenado...")
modelo = tf.keras.models.load_model(MODELO_ENTRENADO)
print("Modelo cargado correctamente.")

# -----------------------------------------
# Convertir a TensorFlow Lite
# -----------------------------------------
print("Convirtiendo modelo a TensorFlow Lite...")

convertidor = tf.lite.TFLiteConverter.from_keras_model(modelo)

# Optimización (reduce tamaño y mejora rendimiento en móviles)
convertidor.optimizations = [tf.lite.Optimize.DEFAULT]

modelo_tflite = convertidor.convert()

# -----------------------------------------
# Guardar archivo .tflite
# -----------------------------------------
with open(MODELO_TFLITE, "wb") as f:
    f.write(modelo_tflite)

print("Conversión finalizada exitosamente.")
print(f"Modelo TensorFlow Lite guardado como: {MODELO_TFLITE}")

# Mostrar tamaño del modelo
tamano_mb = os.path.getsize(MODELO_TFLITE) / (1024 * 1024)
print(f"Tamaño del modelo TFLite: {tamano_mb:.2f} MB")
