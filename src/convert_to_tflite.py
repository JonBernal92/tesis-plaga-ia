# ================================================================
# SCRIPT DE CONVERSIÓN COMPATIBLE (PARA ANDROID)
# Usa este script en lugar del anterior para arreglar el error "Version 12"
# ================================================================

import tensorflow as tf
import os

# ----------------------------------------------------------------
# 1. CONFIGURACIÓN
# ----------------------------------------------------------------
# Nombre exacto con el que guardaste tu modelo en el entrenamiento
# (Según tu código anterior, este es el nombre que genera el script de entrenamiento)
NOMBRE_MODELO_ENTRENADO = "modelo_mobilenetv2_tomate_plagas.h5" 

# Nombre del archivo que vamos a pasar al celular
NOMBRE_MODELO_FINAL = "modelo_tomate_plagas.tflite"

print(f"Usando TensorFlow versión: {tf.__version__}")

# Verificamos que el archivo de entrenamiento exista antes de empezar
if not os.path.exists(NOMBRE_MODELO_ENTRENADO):
    print(f"❌ ERROR: No encuentro el archivo '{NOMBRE_MODELO_ENTRENADO}'")
    print("Asegúrate de haber ejecutado el entrenamiento primero.")
    exit()

# ----------------------------------------------------------------
# 2. CARGAR EL MODELO Keras (.h5)
# ----------------------------------------------------------------
print("Cargando tu modelo entrenado...")

# 'compile=False' hace que cargue más rápido ya que solo queremos la estructura, 
# no necesitamos los optimizadores para convertirlo.
modelo = tf.keras.models.load_model(NOMBRE_MODELO_ENTRENADO, compile=False)

print("✅ Modelo cargado correctamente.")

# ----------------------------------------------------------------
# 3. LA MAGIA: CONFIGURAR EL CONVERTIDOR (Esto arregla el error)
# ----------------------------------------------------------------
print("Iniciando conversión para Android...")

converter = tf.lite.TFLiteConverter.from_keras_model(modelo)

# A. Optimizaciones estándar: Reduce el peso del archivo sin perder mucha precisión
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# B. EL TRUCO DE COMPATIBILIDAD:
# Le decimos al convertidor: "Oye, usa operadores antiguos y estándar".
# Esto evita que cree operadores "Version 12" que tu celular no entiende.
converter.target_spec.supported_ops = [
  tf.lite.OpsSet.TFLITE_BUILTINS, # Usa las funciones básicas de Lite
  tf.lite.OpsSet.SELECT_TF_OPS    # Usa funciones de TF si faltan las de Lite
]

# C. Desactivamos funciones experimentales nuevas para evitar conflictos
converter.experimental_new_converter = True

# ----------------------------------------------------------------
# 4. CONVERTIR Y GUARDAR
# ----------------------------------------------------------------
tflite_model = converter.convert()

with open(NOMBRE_MODELO_FINAL, "wb") as f:
    f.write(tflite_model)

print("\n========================================================")
print(f"🎉 ¡LISTO! Archivo generado: {NOMBRE_MODELO_FINAL}")
print(f"Tamaño: {len(tflite_model) / 1024 / 1024:.2f} MB")
print("========================================================")
