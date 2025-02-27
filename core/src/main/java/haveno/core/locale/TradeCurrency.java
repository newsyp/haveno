/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.core.locale;

import haveno.common.proto.ProtobufferRuntimeException;
import haveno.common.proto.persistable.PersistablePayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public abstract class TradeCurrency implements PersistablePayload, Comparable<TradeCurrency> {
    protected final String code;
    @EqualsAndHashCode.Exclude
    protected final String name;

    public TradeCurrency(String code, String name) {
        this.code = code;
        this.name = name;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TradeCurrency fromProto(protobuf.TradeCurrency proto) {
        switch (proto.getMessageCase()) {
            case FIAT_CURRENCY:
                return FiatCurrency.fromProto(proto);
            case CRYPTO_CURRENCY:
                return CryptoCurrency.fromProto(proto);
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto.getMessageCase());
        }
    }

    public protobuf.TradeCurrency.Builder getTradeCurrencyBuilder() {
        return protobuf.TradeCurrency.newBuilder()
                .setCode(code)
                .setName(name);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getDisplayPrefix() {
        return "";
    }

    public String getNameAndCode() {
        return name + " (" + code + ")";
    }

    public String getCodeAndName() {
        return code + " (" + name + ")";
    }

    @Override
    public int compareTo(@NotNull TradeCurrency other) {
        return this.name.compareTo(other.name);
    }

}
