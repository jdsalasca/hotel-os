#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <zlib.h>

static uLong update_byte(uLong checksum, unsigned char byte) {
  return byte == '\r' || byte == '\n' ? checksum : crc32(checksum, &byte, 1);
}

int main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "uso: flyway-checksum archivo.sql\n");
    return 2;
  }

  FILE *archivo = fopen(argv[1], "rb");
  if (archivo == NULL) {
    perror(argv[1]);
    return 1;
  }

  uLong checksum = crc32(0L, Z_NULL, 0);
  unsigned char inicio[3];
  size_t leidos = fread(inicio, 1, sizeof inicio, archivo);
  size_t primer_byte = leidos == 3 && inicio[0] == 0xEF && inicio[1] == 0xBB && inicio[2] == 0xBF
                           ? 3
                           : 0;

  for (size_t i = primer_byte; i < leidos; i++) {
    checksum = update_byte(checksum, inicio[i]);
  }

  int siguiente;
  while ((siguiente = fgetc(archivo)) != EOF) {
    checksum = update_byte(checksum, (unsigned char)siguiente);
  }

  if (ferror(archivo)) {
    perror(argv[1]);
    fclose(archivo);
    return 1;
  }
  fclose(archivo);

  int64_t valor = checksum >= 0x80000000UL ? (int64_t)checksum - 0x100000000LL : (int64_t)checksum;
  printf("%" PRId64 "\n", valor);
  return 0;
}
