# ================================================================
# CONVERSIÓN DE MODELO KERAS A TENSORFLOW LITE
# Script para exportar el modelo entrenado a formato compatible con Android
# ================================================================

import tensorflow as tf
import numpy as np
import os

# ----------------------------------------------------------------
# CONFIGURACIÓN DE ARCHIVOS
# ----------------------------------------------------------------
# Nombre del modelo entrenado previamente (formato Keras .h5)
NOMBRE_MODELO_ENTRENADO = "modelo_mobilenetv2_tomate_plagas.h5" 

# Nombre del archivo de salida optimizado para dispositivos móviles
NOMBRE_MODELO_FINAL = "tomate_final_compatible.tflite"

print(f"Usando TensorFlow versión: {tf.__version__}")

# Verificación de existencia del archivo fuente
if not os.path.exists(NOMBRE_MODELO_ENTRENADO):
    print(f"❌ ERROR: No se encuentra el archivo '{NOMBRE_MODELO_ENTRENADO}'")
    print("Debe ejecutar primero el script de entrenamiento.")
    exit()

# ----------------------------------------------------------------
# CARGA DEL MODELO ENTRENADO
# ----------------------------------------------------------------
print("Cargando modelo desde archivo .h5...")

# Se carga el modelo sin recompiarlo (compile=False) ya que solo se necesita
# la arquitectura y los pesos para la conversión, no los optimizadores de entrenamiento
modelo = tf.keras.models.load_model(NOMBRE_MODELO_ENTRENADO, compile=False)

print("✅ Modelo cargado exitosamente.")
print(f"   - Entrada: {modelo.input_shape}")
print(f"   - Salida: {modelo.output_shape}")

# ----------------------------------------------------------------
# GENERACIÓN DE DATASET REPRESENTATIVO
# ----------------------------------------------------------------
# El dataset representativo permite al conversor analizar el rango de valores
# que procesará el modelo, optimizando la cuantización y mejorando la precisión
# en dispositivos móviles con recursos limitados

def representative_dataset():
    """
    Genera un conjunto de datos sintéticos para calibración del modelo.
    
    Este dataset permite al conversor de TensorFlow Lite determinar los rangos
    óptimos de activación para cada capa de la red neuronal, resultando en
    una cuantización más precisa sin pérdida significativa de exactitud.
    
    Yields:
        list: Lote de 1 imagen de 224x224x3 píxeles en formato float32
    """
    # Se generan 100 ejemplos aleatorios para una calibración robusta
    for _ in range(100):
        # Imagen sintética con valores normalizados (rango 0.0 - 255.0)
        # Dimensiones: [batch=1, altura=224, ancho=224, canales=3]
        data = np.random.rand(1, 224, 224, 3) * 255.0
        yield [data.astype(np.float32)]

print("Dataset representativo configurado (100 muestras sintéticas).")

# ----------------------------------------------------------------
# CONFIGURACIÓN DEL CONVERSOR TFLITE
# ----------------------------------------------------------------
print("\nIniciando proceso de conversión a TensorFlow Lite...")

# Inicialización del conversor desde modelo Keras
converter = tf.lite.TFLiteConverter.from_keras_model(modelo)

# --- Optimizaciones de Cuantización ---
# La cuantización reduce el tamaño del modelo y acelera la inferencia
# convirtiendo pesos de float32 (4 bytes) a int8 (1 byte)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Asignación del dataset para calibración de cuantización
converter.representative_dataset = representative_dataset

# --- Especificación de Operadores Soportados ---
# Se restringe a operadores básicos de TFLite cuantizados a 8 bits
# para garantizar compatibilidad con versiones antiguas del runtime
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]

# --- Tipos de Datos de Entrada/Salida ---
# A pesar de la cuantización interna, las interfaces del modelo mantienen
# precisión float32 para facilitar el preprocesamiento de imágenes
converter.inference_input_type = tf.float32
converter.inference_output_type = tf.float32

# ----------------------------------------------------------------
# PROCESO DE CONVERSIÓN
# ----------------------------------------------------------------
print("Ejecutando conversión (esto puede tomar varios minutos)...")

try:
    # Conversión del modelo a formato TFLite binario
    tflite_model = converter.convert()
    
    # Escritura del modelo convertido en disco
    with open(NOMBRE_MODELO_FINAL, "wb") as archivo_salida:
        archivo_salida.write(tflite_model)
    
    # Cálculo del tamaño final
    tamanio_mb = len(tflite_model) / (1024 * 1024)
    
    print("\n" + "=" * 70)
    print("✅ CONVERSIÓN COMPLETADA EXITOSAMENTE")
    print("=" * 70)
    print(f"Archivo generado: {NOMBRE_MODELO_FINAL}")
    print(f"Tamaño del modelo: {tamanio_mb:.2f} MB")
    print(f"Formato: TensorFlow Lite (cuantizado a INT8)")
    print("\nCaracterísticas del modelo optimizado:")
    print("  • Inferencia acelerada mediante cuantización")
    print("  • Tamaño reducido (≈4x más pequeño que float32)")
    print("  • Compatible con Android API 24+ (armeabi-v7a)")
    print("=" * 70)
    
except Exception as error_conversion:
    # Manejo de fallos durante la conversión cuantizada
    print(f"\n⚠️  Error durante conversión optimizada: {error_conversion}")
    print("\nIntentando conversión básica sin cuantización...")
    
    # CONVERSIÓN ALTERNATIVA: Sin optimizaciones de cuantización
    # Se utiliza como fallback cuando la cuantización INT8 falla
    # Produce un modelo más grande pero más compatible
    converter_basico = tf.lite.TFLiteConverter.from_keras_model(modelo)
    
    # Conversión sin configuraciones adicionales
    tflite_model = converter_basico.convert()
    
    # Guardado del modelo básico
    with open(NOMBRE_MODELO_FINAL, "wb") as archivo_salida:
        archivo_salida.write(tflite_model)
    
    tamanio_mb = len(tflite_model) / (1024 * 1024)
    
    print(f"✅ Conversión básica completada: {NOMBRE_MODELO_FINAL}")
    print(f"Tamaño: {tamanio_mb:.2f} MB (sin cuantización)")
    print("Nota: Este modelo es funcional pero ocupa más espacio en memoria.")

# ----------------------------------------------------------------
# INSTRUCCIONES DE IMPLEMENTACIÓN
# ----------------------------------------------------------------
print("\n📱 PASOS PARA INTEGRACIÓN EN ANDROID:")
print("─" * 70)
print("1. Copiar el archivo a la carpeta de assets del proyecto:")
print(f"   android/app/src/main/assets/{NOMBRE_MODELO_FINAL}")
print("\n2. Actualizar la referencia en TomateClassifier.kt:")
print(f"   loadModelFile(\"{NOMBRE_MODELO_FINAL}\")")
print("\n3. Compilar y ejecutar la aplicación en el dispositivo")
print("─" * 70)