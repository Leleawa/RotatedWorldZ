% Prolog 层（tuProlog）：/rotate 的参数语法（原来在 Groovy 的 RotateCommand 里），写成 DCG。
%
%   /rotate [on|off|toggle|status|unstuck|reload] [玩家]
%
% rotate_command(参数列表, cmd(动作, 目标)) ；没有目标时目标是 ''。不合语法时失败，命令显示用法。
% 第一个参数已经由 Groovy 转成小写；多余的参数和原来一样被忽略。

rotate_command(Args, cmd(Action, Target)) :- phrase(command(Action, Target), Args).

command(toggle, '') --> [].
command(Action, '') --> action(Action).
command(Action, Target) --> action(Action), [Target], ignored.

action(Action) --> [Action], { member(Action, [on, off, toggle, status, unstuck, reload]) }.

ignored --> [].
ignored --> [_], ignored.

% 摔落伤害陪审团的一员
fall_damage(_, Creative, Spectator, Flying, Gliding, InWater, SlowFalling, 0.0) :-
    member(true, [Creative, Spectator, Flying, Gliding, InWater, SlowFalling]), !.
fall_damage(Fall, _, _, _, _, _, _, Damage) :-
    N is ceiling(Fall - 3.0),
    ( N > 0 -> Damage is N * 1.0 ; Damage = 0.0 ).
