package net.vayzd.orbit.command;

import com.google.common.base.*;
import lombok.*;

public class OrbitCommand implements OrbitCommandExecutor {

    @Override
    public boolean onCommand(OrbitCommandSender sender, String[] args) {

        final CommandLength length = new CommandLength(args.length);
        final CommandArgument argument = new CommandArgument(args, length);
        if (length.eq(0)) {
            return true;
        } else if (length.gt(0)) {
            switch (argument.at(0)) {
                case "groups":
                    return true;
                case "group":
                    return true;
            }
            return true;
        }
        return false;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class CommandArgument {

        private final String[] argumentArray;
        private final CommandLength length;

        public final String at(int index) {
            Preconditions.checkState(index >= 0);
            Preconditions.checkState(length.get() > index);
            return argumentArray[index];
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class CommandLength {

        private final int length;

        public final int get() {
            return length;
        }

        public final boolean eq(final int toCheck) {
            return length == toCheck;
        }

        public final boolean neq(final int toCheck) {
            return !eq(toCheck);
        }

        public final boolean gt(final int toCheck) {
            return length > toCheck;
        }

        public final boolean gte(final int toCheck) {
            return length >= toCheck;
        }

        public final boolean lt(final int toCheck) {
            return length < toCheck;
        }

        public final boolean lte(final int toCheck) {
            return length <= toCheck;
        }
    }
}
