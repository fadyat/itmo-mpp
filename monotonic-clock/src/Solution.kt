/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author : Fadeyev Artyom
 */
class Solution : MonotonicClock {
    private var c11 by RegularInt(0)
    private var c12 by RegularInt(0)
    private var c13 by RegularInt(0)

    private var c21 by RegularInt(0)
    private var c22 by RegularInt(0)
    private var c23 by RegularInt(0)


    override fun write(time: Time) {
        c21 = time.d1
        c22 = time.d2
        c23 = time.d3

        c13 = c23
        c12 = c22
        c11 = c21
    }

    override fun read(): Time {
        val r11 = c11
        val r12 = c12
        val r13 = c13

        val r23 = c23
        val r22 = c22
        val r21 = c21

        return when {
            Time(r11, r12, r13) == Time(r21, r22, r23) -> Time(r11, r12, r13)
            r11 != r21 -> Time(r21, 0, 0)
            r12 != r22 -> Time(r21, r22, 0)
            else -> Time(r21, r22, r23)
        }
    }
}